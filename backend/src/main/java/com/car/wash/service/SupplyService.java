package com.car.wash.service;

import com.car.wash.dto.BizException;
import com.car.wash.entity.Supply;
import com.car.wash.enums.SupplyState;
import com.car.wash.repository.SupplyRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplyService {

    private final SupplyRepository supplies;

    public SupplyService(SupplyRepository supplies) {
        this.supplies = supplies;
    }

    public List<Supply> list(SupplyState state, String keyword) {
        return supplies.findAllByOrderByIdAsc().stream()
                .filter(s -> state == null || state == s.supplyState)
                .filter(s -> keyword == null || keyword.isBlank()
                        || s.supplyCode.contains(keyword) || s.supplyName.contains(keyword))
                .toList();
    }

    @Transactional
    public Supply save(Supply form) {
        Supply existed = null;
        if (form.id != null) {
            existed = supplies.findById(form.id).orElseThrow(() -> new BizException("这批耗材不存在"));
            if (form.supplyCode == null || form.supplyCode.isBlank()) {
                form.supplyCode = existed.supplyCode;
            }
            if (form.supplyName == null || form.supplyName.isBlank()) {
                form.supplyName = existed.supplyName;
            }
        }
        if (form.supplyCode == null || form.supplyName == null) {
            throw new BizException("耗材编号和名称都得填");
        }
        supplies.findBySupplyCode(form.supplyCode).ifPresent(o -> {
            if (!o.id.equals(form.id)) {
                throw new BizException("耗材编号 " + form.supplyCode + " 重复了");
            }
        });
        if (form.stock != null && form.stock < 0) {
            throw new BizException("库存不能是负数");
        }
        if (existed == null) {
            form.supplyState = form.supplyState == null ? SupplyState.正常 : form.supplyState;
            return supplies.save(form);
        }
        if (form.unitText != null && !form.unitText.isBlank()) {
            existed.unitText = form.unitText;
        }
        if (form.warnLine != null) {
            existed.warnLine = form.warnLine;
        }
        if (form.stock != null) {
            existed.stock = form.stock;
            existed.supplyState = form.stock == 0 ? SupplyState.已用完
                    : (existed.warnLine != null && form.stock < existed.warnLine ? SupplyState.不足 : SupplyState.正常);
        }
        if (form.supplyState != null) {
            existed.supplyState = form.supplyState;
        }
        existed.supplyCode = form.supplyCode;
        existed.supplyName = form.supplyName;
        return supplies.save(existed);
    }

    /** 快捷出库：给前端一个「用掉几件」的口子。 */
    @Transactional
    public Supply consume(Long id, Map<String, Object> body) {
        Supply supply = supplies.findById(id).orElseThrow(() -> new BizException("这批耗材不存在"));
        Object qty = body.get("quantity");
        Integer n = qty == null || String.valueOf(qty).isBlank() ? 1 : Integer.valueOf(String.valueOf(qty));
        supply.consume(n, supply.warnLine);
        return supplies.save(supply);
    }
}

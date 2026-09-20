package com.car.wash.service;

import com.car.wash.dto.BizException;
import com.car.wash.entity.Bay;
import com.car.wash.enums.BayState;
import com.car.wash.repository.BayRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BayService {

    private final BayRepository bays;
    private final BayOccupancyService occupancy;

    public BayService(BayRepository bays, BayOccupancyService occupancy) {
        this.bays = bays;
        this.occupancy = occupancy;
    }

    public List<Bay> list(BayState state, String keyword) {
        return bays.findAllByOrderByIdAsc().stream()
                .peek(occupancy::fill)
                .filter(b -> state == null || state == b.bayState)
                .filter(b -> keyword == null || keyword.isBlank()
                        || b.bayCode.contains(keyword) || b.bayName.contains(keyword))
                .toList();
    }

    @Transactional
    public Bay save(Bay form) {
        Bay existed = null;
        if (form.id != null) {
            existed = bays.findById(form.id).orElseThrow(() -> new BizException("工位不存在"));
            if (form.bayCode == null || form.bayCode.isBlank()) {
                form.bayCode = existed.bayCode;
            }
            if (form.bayName == null || form.bayName.isBlank()) {
                form.bayName = existed.bayName;
            }
        }
        if (form.bayCode == null || form.bayName == null) {
            throw new BizException("工位编号和名称都得填");
        }
        bays.findByBayCode(form.bayCode).ifPresent(o -> {
            if (!o.id.equals(form.id)) {
                throw new BizException("工位编号 " + form.bayCode + " 重复了");
            }
        });
        if (existed == null) {
            if (form.bayState == null) {
                form.bayState = BayState.空闲;
            }
            if (form.seatCount != null && form.seatCount <= 0) {
                throw new BizException("同时容纳得是正数");
            }
            return bays.save(form);
        }
        // 停用只看没洗完的洗车单；回炉中的车不拦停用——那张回炉不拆，只能改派到别的空闲工位。
        if (form.bayState == BayState.停用 && existed.bayState != BayState.停用
                && occupancy.washingCount(form.id) > 0) {
            throw new BizException("这个工位上还有没洗完的单子，先处理完再停用（回炉中的车请用改派挪走）");
        }
        if (form.seatCount != null) {
            if (form.seatCount <= 0) {
                throw new BizException("同时容纳得是正数");
            }
            existed.seatCount = form.seatCount;
        }
        if (form.bayState != null) {
            existed.bayState = form.bayState;
        }
        existed.bayCode = form.bayCode;
        existed.bayName = form.bayName;
        return bays.save(existed);
    }
}

package com.car.wash.entity;

import com.car.wash.enums.SupplyState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 洗车耗材。 */
@Entity
@Table(name = "supply")
public class Supply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "supply_code", nullable = false, length = 20, unique = true)
    public String supplyCode;

    @Column(name = "supply_name", nullable = false, length = 60)
    public String supplyName;

    @Column(name = "unit_text", length = 12)
    public String unitText;

    @Column(name = "stock", nullable = false)
    public Integer stock;

    @Column(name = "warn_line")
    public Integer warnLine;

    @Enumerated(EnumType.STRING)
    @Column(name = "supply_state", nullable = false, length = 12)
    public SupplyState supplyState;

    /** 出库：不够就别出，出到 0 自动标成已用完。 */
    public void consume(Integer qty, Integer warnLine) {
        if (qty == null || qty <= 0) {
            throw new com.car.wash.dto.BizException("出库数量得大于 0");
        }
        if (stock == null || stock < qty) {
            throw new com.car.wash.dto.BizException("「" + supplyName + "」只剩 " + stock + "，出不了 " + qty);
        }
        stock -= qty;
        if (stock == 0) {
            supplyState = SupplyState.已用完;
        } else if (warnLine != null && stock < warnLine) {
            supplyState = SupplyState.不足;
        } else {
            supplyState = SupplyState.正常;
        }
    }

    /**
     * 回炉没验收通过（作废），把二次领走的料整笔退回货架：
     * 数量加回，状态按现库存重新归位（已用完/不足 → 回到正常或不足）。
     */
    public void restore(Integer qty, Integer warnLine) {
        if (qty == null || qty <= 0) {
            throw new com.car.wash.dto.BizException("退料数量得大于 0");
        }
        stock = (stock == null ? 0 : stock) + qty;
        if (warnLine != null && stock < warnLine) {
            supplyState = SupplyState.不足;
        } else {
            supplyState = SupplyState.正常;
        }
    }
}

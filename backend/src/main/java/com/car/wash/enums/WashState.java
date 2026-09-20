package com.car.wash.enums;

/**
 * 洗车单的状态机：待洗 → 清洗中 → 已完成。
 * 「已撤销」是整笔撤单的终态，不走 canMoveTo（见 WashOrderService.voidOrder，撤单要连同卡账一起了）。
 */
public enum WashState {
    待洗, 清洗中, 已完成, 已撤销;

    /** 能不能从当前状态走到目标状态（只许往前走一步；撤销不许走这条，得走 voidOrder 连带退卡账）。 */
    public boolean canMoveTo(WashState target) {
        if (target == null || target == 已撤销 || this == 已撤销) {
            return false;
        }
        return target.ordinal() == this.ordinal() + 1;
    }
}

package com.car.wash.enums;

/**
 * 回炉单状态机：待回炉 → 回炉中 → 已验收；没验收通过（客人仍不满意）只能「作废」。
 * 只许顺着走一步，不许跳步，也不许倒回去。作废是终态，不走 canMoveTo（见 ReworkService.abort）。
 */
public enum ReworkState {
    待回炉, 回炉中, 已验收, 已作废;

    /** 能不能从当前状态顺着走到目标状态（只许往前走一步；验收、作废都是终态）。 */
    public boolean canMoveTo(ReworkState target) {
        if (target == null || this == 已验收 || this == 已作废 || target == 已作废) {
            return false;
        }
        return target.ordinal() == this.ordinal() + 1;
    }

    /** 还没验收、也没作废的回炉才算「回炉未结」。 */
    public boolean isOpen() {
        return this == 待回炉 || this == 回炉中;
    }
}

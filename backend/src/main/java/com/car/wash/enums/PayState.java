package com.car.wash.enums;

/** 洗车单的卡账状态：未支付 → 已支付（钉了一笔成功扣款）→ 已退款（整笔撤单后钱退回原卡）。 */
public enum PayState {
    未支付, 已支付, 已退款
}

package com.car.wash.entity;

import com.car.wash.enums.FlowType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDateTime;

/**
 * 卡流水：卡上每一笔余额变动都落一行，钉死在「哪张卡、哪张洗车单、多少钱、这笔之后卡上剩多少」。
 * 晚班对账就按这张表从卡流水找到洗车单；没钉单号的余额变动根本不允许发生。
 */
@Entity
@Table(name = "card_flow")
public class CardFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 流水号，对账嘴上说的就是这个号。 */
    @Column(name = "flow_no", nullable = false, length = 24, unique = true)
    public String flowNo;

    @Column(name = "card_id", nullable = false)
    public Long cardId;

    /** 钉住的洗车单：扣款是这单结的账，退款也是这单撤出来的。 */
    @Column(name = "order_id", nullable = false)
    public Long orderId;

    /** 金额，正数；是扣是退看 flowType。 */
    @Column(name = "amount", nullable = false)
    public Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "flow_type", nullable = false, length = 8)
    public FlowType flowType;

    /** 这笔走完卡上的余额，对账时一眼对上卡页数字。 */
    @Column(name = "balance_after", nullable = false)
    public Integer balanceAfter;

    /** 退款行指向原来那笔扣款，一笔扣款只许有一笔退款。 */
    @Column(name = "ref_flow_id")
    public Long refFlowId;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    // —— 对账列表带出的冗余，不落库 ——

    @Transient
    public String cardNo;

    @Transient
    public String holderName;

    @Transient
    public String orderNo;
}

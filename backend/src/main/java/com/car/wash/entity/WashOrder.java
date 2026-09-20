package com.car.wash.entity;

import com.car.wash.enums.PayState;
import com.car.wash.enums.WashState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDate;

/** 洗车单。 */
@Entity
@Table(name = "wash_order")
public class WashOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "order_no", nullable = false, length = 20, unique = true)
    public String orderNo;

    @Column(name = "plate_no", nullable = false, length = 16)
    public String plateNo;

    @Column(name = "bay_id")
    public Long bayId;

    @Column(name = "service_type", length = 24)
    public String serviceType;

    @Column(name = "price")
    public Integer price;

    @Column(name = "order_date")
    public LocalDate orderDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "wash_state", nullable = false, length = 12)
    public WashState washState;

    // —— 卡账：这单有没有刷过卡、是哪一笔扣的，都钉在单上 ——

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_state", nullable = false, length = 8)
    public PayState payState = PayState.未支付;

    /** 成功那笔扣款的流水号（card_flow.id），对账从单找到流水就靠它。 */
    @Column(name = "pay_flow_id")
    public Long payFlowId;

    // —— 回炉未结口径由 service 带出，不落库 ——

    /** 还挂着未验收回炉（待回炉 / 回炉中）。原单仍停在已完成，但列表和详情都要看得见。 */
    @Transient
    public Boolean reworkOpen;

    /** 挂着的未验收回炉单号，没有就是空。 */
    @Transient
    public String openReworkNo;

    // —— 卡账展示口径由 service 带出，不落库 ——

    /** 成功那笔扣款的流水号（CF-xx）。 */
    @Transient
    public String payFlowNo;

    /** 这单刷的是哪张卡。 */
    @Transient
    public String payCardNo;
}

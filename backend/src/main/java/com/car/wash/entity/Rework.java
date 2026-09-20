package com.car.wash.entity;

import com.car.wash.enums.ReworkState;
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
import java.util.List;

/**
 * 回炉单：客人洗完又挑毛病时挂在已完成洗车单上的返工记录。
 * 一张原单同时只能挂一张未验收的回炉；回炉自己按「待回炉 → 回炉中 → 已验收」走。
 */
@Entity
@Table(name = "rework")
public class Rework {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "rework_no", nullable = false, length = 20, unique = true)
    public String reworkNo;

    /** 挂在哪张已完成的洗车单上。 */
    @Column(name = "order_id", nullable = false)
    public Long orderId;

    /** 回炉占的工位：挂上时先认原单工位，认不上才改派；回炉中工位停用还可以再改派。 */
    @Column(name = "bay_id", nullable = false)
    public Long bayId;

    @Column(name = "reason", length = 120)
    public String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "rework_state", nullable = false, length = 12)
    public ReworkState reworkState;

    @Column(name = "created_date")
    public LocalDate createdDate;

    // —— 以下只是给列表/详情带出来的展示口径，不落库 ——

    @Transient
    public String orderNo;

    @Transient
    public String plateNo;

    @Transient
    public String bayCode;

    @Transient
    public String bayName;

    /** true 表示占的不是原单工位，是认不上之后改派来的（原工位停用/满位）。 */
    @Transient
    public Boolean reassigned;

    /**
     * 挂回炉时一起带来的二次领料清单（泡沫、毛巾各一行），不落库，真正的账在 rework_supply 表。
     * 列表/详情再把已出账的领料行回填进来。
     */
    @Transient
    public List<ReworkSupply> supplyLines;
}

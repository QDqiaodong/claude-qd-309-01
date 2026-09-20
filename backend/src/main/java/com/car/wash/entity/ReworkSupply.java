package com.car.wash.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * 回炉二次领料的一行：挂回炉的同时按行从货架出库（泡沫、毛巾）。
 * 回炉没验收通过（作废）时逐行退回货架，验收通过则留作出库、不再加回。
 * returned 只在退料后置 1，是仓管晚上对货的凭据。
 */
@Entity
@Table(name = "rework_supply")
public class ReworkSupply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "rework_id", nullable = false)
    public Long reworkId;

    @Column(name = "supply_id", nullable = false)
    public Long supplyId;

    /** 这次回炉再拿几件（领料数量，也是将来退料数量，整笔同退）。 */
    @Column(name = "quantity", nullable = false)
    public Integer quantity;

    /** 0 = 已出库未退；1 = 回炉作废，料已退回货架。验收通过的一直是 0。 */
    @Column(name = "returned", nullable = false)
    public Boolean returned = false;

    // —— 以下只是给列表带出来的展示口径，不落库 ——

    @Transient
    public String supplyCode;

    @Transient
    public String supplyName;

    @Transient
    public String unitText;
}

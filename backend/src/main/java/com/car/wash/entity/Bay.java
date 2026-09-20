package com.car.wash.entity;

import com.car.wash.enums.BayState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/** 洗车工位。 */
@Entity
@Table(name = "bay")
public class Bay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "bay_code", nullable = false, length = 20, unique = true)
    public String bayCode;

    @Column(name = "bay_name", nullable = false, length = 60)
    public String bayName;

    @Column(name = "seat_count")
    public Integer seatCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "bay_state", nullable = false, length = 12)
    public BayState bayState;

    // —— 占用口径由 BayOccupancyService 统一带出，不落库 ——

    /** 没洗完的洗车单占的位数。 */
    @Transient
    public Integer washingCars;

    /** 回炉中的车占的位数。 */
    @Transient
    public Integer reworkingCars;

    /** 总共占了几位（洗车 + 回炉）。 */
    @Transient
    public Integer occupiedSeats;

    /** 还空几位。 */
    @Transient
    public Integer freeSeats;
}

package com.car.wash.service;

import com.car.wash.dto.BizException;
import com.car.wash.entity.Bay;
import com.car.wash.enums.BayState;
import com.car.wash.enums.ReworkState;
import com.car.wash.enums.WashState;
import com.car.wash.repository.BayRepository;
import com.car.wash.repository.ReworkRepository;
import com.car.wash.repository.WashOrderRepository;
import java.util.EnumSet;
import org.springframework.stereotype.Service;

/**
 * 工位占用的统一口径，开单、挂回炉、改派、工位列表都认这一份：
 * 一个工位上「没洗完的洗车单（待洗/清洗中）」+「回炉中的车」一起算占座；
 * 已完成的单和待回炉（车还没进来）不占座。
 */
@Service
public class BayOccupancyService {

    /** 还占着工位的洗车单状态。 */
    public static final EnumSet<WashState> WASHING = EnumSet.of(WashState.待洗, WashState.清洗中);

    private final BayRepository bays;
    private final WashOrderRepository orders;
    private final ReworkRepository reworks;

    public BayOccupancyService(BayRepository bays, WashOrderRepository orders, ReworkRepository reworks) {
        this.bays = bays;
        this.orders = orders;
        this.reworks = reworks;
    }

    public Bay requireBay(Long bayId) {
        return bays.findById(bayId == null ? -1L : bayId)
                .orElseThrow(() -> new BizException("工位不存在"));
    }

    /** 工位同时容纳，没填就按 1 个位算（和现有工位一致）。 */
    public int seatCount(Bay bay) {
        return bay.seatCount == null || bay.seatCount <= 0 ? 1 : bay.seatCount;
    }

    public long washingCount(Long bayId) {
        return orders.countByBayIdAndWashStateIn(bayId, WASHING.stream().toList());
    }

    /** 回炉中的车才占座，待回炉的车还没进场。 */
    public long reworkingCount(Long bayId) {
        return reworks.findByBayIdAndReworkState(bayId, ReworkState.回炉中).size();
    }

    public long occupiedSeats(Long bayId) {
        return washingCount(bayId) + reworkingCount(bayId);
    }

    public boolean isFull(Bay bay) {
        return occupiedSeats(bay.id) >= seatCount(bay);
    }

    /**
     * 这个工位能不能再进一辆（不管是新单还是回炉）：
     * 停用当场说清原因；满了也当场说清原因。
     */
    public void requireCanAccept(Bay bay, String who) {
        if (bay.bayState == BayState.停用) {
            throw new BizException("工位「" + bay.bayName + "」已经停用，" + who + "排不进去");
        }
        long used = occupiedSeats(bay.id);
        if (used >= seatCount(bay)) {
            throw new BizException(
                    "工位「" + bay.bayName + "」已经满了（容纳 " + seatCount(bay) + " 辆，已占 " + used + " 辆），" + who + "排不进去");
        }
    }

    /** 工位列表用：把占用数直接带出去，前端不用各算各的。 */
    public Bay fill(Bay bay) {
        bay.washingCars = (int) washingCount(bay.id);
        bay.reworkingCars = (int) reworkingCount(bay.id);
        bay.occupiedSeats = bay.washingCars + bay.reworkingCars;
        bay.freeSeats = Math.max(0, seatCount(bay) - bay.occupiedSeats);
        return bay;
    }
}

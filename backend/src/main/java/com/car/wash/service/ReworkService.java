package com.car.wash.service;

import com.car.wash.dto.BizException;
import com.car.wash.entity.Bay;
import com.car.wash.entity.Rework;
import com.car.wash.entity.ReworkSupply;
import com.car.wash.entity.Supply;
import com.car.wash.entity.WashOrder;
import com.car.wash.enums.BayState;
import com.car.wash.enums.ReworkState;
import com.car.wash.enums.WashState;
import com.car.wash.repository.BayRepository;
import com.car.wash.repository.ReworkRepository;
import com.car.wash.repository.ReworkSupplyRepository;
import com.car.wash.repository.SupplyRepository;
import com.car.wash.repository.WashOrderRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 回炉台规则（按店长口径）：
 * 1. 只能挂在「已完成」的洗车单上；同一张原单只要还有未验收回炉，不得再挂第二张。
 * 2. 回炉自己按「待回炉 → 回炉中 → 已验收」走，不跳步、不倒退；客人仍不满意只能「作废」（终态）。
 * 3. 挂上必须先认原单工位；原工位停用或被别的车占满，才允许改派到别的空闲工位。
 * 4. 回炉中算工位占用；满位的工位接不了新回炉，开新单也一样（见 WashOrderService）。
 * 5. 回炉中工位被停用：单子不拆，只能改派到别的空闲工位；选不到空位，改派失败、状态停在回炉中。
 *
 * 回炉二次领料（按仓管口径）：
 * - 挂回炉的同时必须写清这次要再拿的泡沫、毛巾各几件；货架现数不够，整笔领料失败，回炉也挂不上。
 * - 不许先挂单后补料：料先从货架扣到账上，回炉才允许挂上（同一笔事务，扣不动就整笔回滚）。
 * - 回炉做到一半客人仍不满意、没有验收通过（作废）：二次领料逐行退回货架；验收通过则库存不再加回。
 * - 二次领料只动耗材库存，会员卡余额、洗车单原价一律不参与。
 */
@Service
public class ReworkService {

    private static final List<ReworkState> OPEN_STATES =
            List.of(ReworkState.待回炉, ReworkState.回炉中);

    /** 二次领料必须出现的两类耗材，名称里带这两个词才算对上号。 */
    private static final String FOAM_KEYWORD = "泡沫";
    private static final String TOWEL_KEYWORD = "毛巾";

    private final ReworkRepository reworks;
    private final WashOrderRepository orders;
    private final BayRepository bays;
    private final BayOccupancyService occupancy;
    private final SupplyRepository supplies;
    private final ReworkSupplyRepository reworkSupplies;

    public ReworkService(ReworkRepository reworks, WashOrderRepository orders,
                         BayRepository bays, BayOccupancyService occupancy,
                         SupplyRepository supplies, ReworkSupplyRepository reworkSupplies) {
        this.reworks = reworks;
        this.orders = orders;
        this.bays = bays;
        this.occupancy = occupancy;
        this.supplies = supplies;
        this.reworkSupplies = reworkSupplies;
    }

    public List<Rework> list(ReworkState state, String keyword) {
        List<Rework> all = enrich(reworks.findAllByOrderByIdAsc());
        return all.stream()
                .filter(r -> state == null || state == r.reworkState)
                .filter(r -> keyword == null || keyword.isBlank()
                        || r.reworkNo.contains(keyword)
                        || (r.orderNo != null && r.orderNo.contains(keyword))
                        || (r.plateNo != null && r.plateNo.contains(keyword)))
                .sorted((a, b) -> b.id.compareTo(a.id))
                .toList();
    }

    /** 洗车单打标用：哪些原单还挂着未验收回炉。 */
    public Map<Long, Rework> openByOrderIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        return reworks.findByReworkStateIn(OPEN_STATES).stream()
                .filter(r -> orderIds.contains(r.orderId))
                .collect(Collectors.toMap(r -> r.orderId, Function.identity(), (a, b) -> a));
    }

    /** 原单上有没有未验收回炉。 */
    public List<Rework> openOfOrder(Long orderId) {
        return reworks.findByOrderIdAndReworkStateIn(orderId, OPEN_STATES);
    }

    /**
     * 挂回炉：原单必须已完成且没有未结回炉；工位先认原单。
     * 原工位认不上（停用/满位）才接受改派，且只能改派到空闲（没停用、还有空位）的工位。
     * 挂上同时办二次领料：泡沫、毛巾各写清几件，先扣货架再挂单，任一样不够都整笔失败。
     */
    @Transactional
    public Rework create(Rework form) {
        if (form.orderId == null) {
            throw new BizException("得先说清这是哪张洗车单的回炉");
        }
        WashOrder origin = orders.findById(form.orderId)
                .orElseThrow(() -> new BizException("原洗车单不存在"));
        if (origin.washState != WashState.已完成) {
            throw new BizException("只有走到「已完成」的单子才能挂回炉，单子「" + origin.orderNo + "」现在是「"
                    + origin.washState + "」");
        }
        if (!reworks.findByOrderIdAndReworkStateIn(origin.id, OPEN_STATES).isEmpty()) {
            throw new BizException("原单「" + origin.orderNo + "」还有没验收的回炉，不能再挂第二张");
        }
        if (origin.bayId == null) {
            throw new BizException("原单「" + origin.orderNo + "」没有工位记录，没法先认原工位");
        }
        Bay originBay = occupancy.requireBay(origin.bayId);

        Long bayId;
        if (form.bayId == null || form.bayId.equals(originBay.id)) {
            // 先认原工位：停用或满位都要当场把原因说清楚，不许偷偷塞到别处。
            occupancy.requireCanAccept(originBay, "回炉");
            bayId = originBay.id;
        } else {
            // 原工位还能进车（没停用、没满），就不许嫌麻烦随便找空位。
            if (originBay.bayState != BayState.停用 && !occupancy.isFull(originBay)) {
                throw new BizException("原工位「" + originBay.bayName + "」还空着，回炉得先认原工位，不能随便改派");
            }
            Bay target = occupancy.requireBay(form.bayId);
            occupancy.requireCanAccept(target, "回炉");
            bayId = target.id;
        }

        // 二次领料：必须写清再拿的泡沫、毛巾各几件；先把料扣到账上，才允许挂回炉。
        List<DrawnSupply> drawn = drawSupplies(form.supplyLines);

        Rework rw = new Rework();
        rw.reworkNo = nextNo();
        rw.orderId = origin.id;
        rw.bayId = bayId;
        rw.reason = form.reason;
        rw.reworkState = ReworkState.待回炉;
        rw.createdDate = LocalDate.now();
        Rework saved = reworks.save(rw);

        List<ReworkSupply> lines = new ArrayList<>();
        for (DrawnSupply d : drawn) {
            ReworkSupply line = new ReworkSupply();
            line.reworkId = saved.id;
            line.supplyId = d.supply.id;
            line.quantity = d.quantity;
            line.returned = false;
            lines.add(reworkSupplies.save(line));
        }
        saved.supplyLines = lines;
        return enrichOne(saved);
    }

    /** 顺着状态机往前走一步。 */
    @Transactional
    public Rework advance(Long id) {
        Rework rw = mustGet(id);
        if (rw.reworkState == ReworkState.已验收) {
            throw new BizException("这张回炉已经验收过了，流程到头了");
        }
        if (rw.reworkState == ReworkState.已作废) {
            throw new BizException("这张回炉客人没认、已经作废，二次领料也退回货架了，不能再往前走");
        }
        ReworkState next = rw.reworkState == ReworkState.待回炉
                ? ReworkState.回炉中 : ReworkState.已验收;
        if (!rw.reworkState.canMoveTo(next)) {
            throw new BizException("回炉得按「待回炉 → 回炉中 → 已验收」走，不能跳步");
        }
        Bay bay = occupancy.requireBay(rw.bayId);
        if (next == ReworkState.回炉中) {
            // 车进场要占座：工位停用或满了（被别的车占满）就进不去，状态留在待回炉。
            occupancy.requireCanAccept(bay, "回炉");
        } else {
            // 验收前工位被停用了：单子不能偷偷变已验收，得先改派到空闲工位做完。
            if (bay.bayState == BayState.停用) {
                throw new BizException("工位「" + bay.bayName + "」已经停用，先把这张回炉改派到空闲工位再验收");
            }
            // 验收通过：二次领的泡沫、毛巾算正式用掉，库存不再加回。
        }
        rw.reworkState = next;
        return enrichOne(reworks.save(rw));
    }

    /**
     * 客人做到一半仍不满意、这条回炉验收不过：作废。
     * 二次领料逐行退回货架（数量加回、状态按现库存归位）；验收通过的回炉不许作废，料也不退。
     */
    @Transactional
    public Rework abort(Long id) {
        Rework rw = mustGet(id);
        if (rw.reworkState == ReworkState.已验收) {
            throw new BizException("已经验收通过的回炉不能作废，二次领的料算正式用掉，不退架");
        }
        if (rw.reworkState == ReworkState.已作废) {
            throw new BizException("这张回炉已经作废过了");
        }

        List<ReworkSupply> lines = reworkSupplies.findByReworkIdOrderByIdAsc(rw.id);
        for (ReworkSupply line : lines) {
            if (Boolean.TRUE.equals(line.returned)) {
                continue;
            }
            Supply supply = supplies.findById(line.supplyId)
                    .orElseThrow(() -> new BizException("二次领的耗材在货架上找不到记录了，退不回去"));
            supply.restore(line.quantity, supply.warnLine);
            supplies.save(supply);
            line.returned = true;
            reworkSupplies.save(line);
        }

        rw.reworkState = ReworkState.已作废;
        return enrichOne(reworks.save(rw));
    }

    /**
     * 改派工位：待回炉 / 回炉中都可以改派，但目标必须是空闲工位（没停用、还有空位）。
     * 选不到空位就整次失败，回炉状态原样不动。回炉中的车不会被拆掉。
     */
    @Transactional
    public Rework reassign(Long id, Long targetBayId) {
        Rework rw = mustGet(id);
        if (rw.reworkState == ReworkState.已验收) {
            throw new BizException("已经验收的回炉不用再改派了");
        }
        if (rw.reworkState == ReworkState.已作废) {
            throw new BizException("已经作废的回炉不用再改派了");
        }
        Bay target = occupancy.requireBay(targetBayId);
        occupancy.requireCanAccept(target, "回炉");
        rw.bayId = target.id;
        return enrichOne(reworks.save(rw));
    }

    /**
     * 按仓管口径办二次领料：必须正好是「泡沫」和「毛巾」各一行、数量为正整数；
     * 先把两行库存都验一遍（任一样现数不够，整笔领料失败、一件都不动），
     * 再逐行扣到账上。全程同一事务，后面挂回炉若失败也会一起回滚。
     */
    private List<DrawnSupply> drawSupplies(List<ReworkSupply> formLines) {
        if (formLines == null || formLines.isEmpty()) {
            throw new BizException("回炉得再领一次料：写清这次要拿的泡沫和毛巾各几件");
        }
        List<Long> supplyIds = new ArrayList<>();
        boolean hasFoam = false;
        boolean hasTowel = false;
        for (ReworkSupply line : formLines) {
            if (line == null || line.supplyId == null) {
                throw new BizException("二次领料每行都得选是哪样耗材（泡沫、毛巾）");
            }
            if (!supplyIds.add(line.supplyId)) {
                throw new BizException("同一样耗材别分成两行写，泡沫、毛巾各一行就够");
            }
            if (line.quantity == null || line.quantity <= 0) {
                throw new BizException("二次领料数量得是大于 0 的整数");
            }
        }
        if (formLines.size() != 2 || supplyIds.size() != 2) {
            throw new BizException("二次领料就两样：泡沫一行、毛巾一行，多一样少一样都不给出");
        }

        List<Supply> picked = supplies.findAllById(supplyIds);
        Map<Long, Supply> byId = picked.stream()
                .collect(Collectors.toMap(s -> s.id, Function.identity()));
        List<DrawnSupply> drawn = new ArrayList<>();
        for (ReworkSupply line : formLines) {
            Supply supply = byId.get(line.supplyId);
            if (supply == null) {
                throw new BizException("选的耗材货架上不存在，重新选泡沫和毛巾");
            }
            boolean foam = supply.supplyName != null && supply.supplyName.contains(FOAM_KEYWORD);
            boolean towel = supply.supplyName != null && supply.supplyName.contains(TOWEL_KEYWORD);
            if (!foam && !towel) {
                throw new BizException("回炉二次领料只认泡沫和毛巾，「" + supply.supplyName + "」不在这两样里");
            }
            if (foam) {
                hasFoam = true;
            } else {
                hasTowel = true;
            }
            drawn.add(new DrawnSupply(supply, line.quantity));
        }
        if (!hasFoam || !hasTowel) {
            throw new BizException("二次领料得写清泡沫和毛巾各几件，现在缺了「"
                    + (!hasFoam ? "泡沫" : "毛巾") + "」");
        }

        // 先验后扣：任一样现数不够都整笔失败，不许扣一样挂着半笔账。
        for (DrawnSupply d : drawn) {
            int stock = d.supply.stock == null ? 0 : d.supply.stock;
            if (stock < d.quantity) {
                throw new BizException("「" + d.supply.supplyName + "」货架现数只剩 " + stock
                        + "，这回要再拿 " + d.quantity + "，不够；整笔领料失败，回炉挂不上，先补货再挂");
            }
        }
        for (DrawnSupply d : drawn) {
            d.supply.consume(d.quantity, d.supply.warnLine);
            supplies.save(d.supply);
        }
        return drawn;
    }

    private Rework mustGet(Long id) {
        return reworks.findById(id).orElseThrow(() -> new BizException("回炉单不存在"));
    }

    private String nextNo() {
        for (long i = reworks.count() + 1; ; i++) {
            String no = String.format("RW-%02d", i);
            if (!reworks.existsByReworkNo(no)) {
                return no;
            }
        }
    }

    private List<Rework> enrich(List<Rework> rows) {
        if (rows.isEmpty()) {
            return rows;
        }
        Map<Long, WashOrder> orderMap = orders.findAllById(
                rows.stream().map(r -> r.orderId).distinct().toList()).stream()
                .collect(Collectors.toMap(o -> o.id, Function.identity()));
        Map<Long, Bay> bayMap = bays.findAllById(
                rows.stream().map(r -> r.bayId).distinct().toList()).stream()
                .collect(Collectors.toMap(b -> b.id, Function.identity()));

        List<ReworkSupply> allLines = reworkSupplies.findByReworkIdInOrderByIdAsc(
                rows.stream().map(r -> r.id).toList());
        Map<Long, Supply> supplyMap = supplies.findAllById(
                allLines.stream().map(l -> l.supplyId).distinct().toList()).stream()
                .collect(Collectors.toMap(s -> s.id, Function.identity()));
        Map<Long, List<ReworkSupply>> lineMap = allLines.stream()
                .collect(Collectors.groupingBy(l -> l.reworkId));
        for (ReworkSupply line : allLines) {
            Supply s = supplyMap.get(line.supplyId);
            if (s != null) {
                line.supplyCode = s.supplyCode;
                line.supplyName = s.supplyName;
                line.unitText = s.unitText;
            }
        }

        for (Rework r : rows) {
            WashOrder o = orderMap.get(r.orderId);
            if (o != null) {
                r.orderNo = o.orderNo;
                r.plateNo = o.plateNo;
                r.reassigned = o.bayId != null && !o.bayId.equals(r.bayId);
            }
            Bay b = bayMap.get(r.bayId);
            if (b != null) {
                r.bayCode = b.bayCode;
                r.bayName = b.bayName;
            }
            r.supplyLines = lineMap.getOrDefault(r.id, List.of());
        }
        return rows;
    }

    private Rework enrichOne(Rework rw) {
        enrich(List.of(rw));
        return rw;
    }

    /** 内存里把「要出的料」和数量绑一起。 */
    private record DrawnSupply(Supply supply, Integer quantity) {
    }
}

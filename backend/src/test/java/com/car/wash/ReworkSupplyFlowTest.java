package com.car.wash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.car.wash.dto.BizException;
import com.car.wash.entity.Bay;
import com.car.wash.entity.Rework;
import com.car.wash.entity.ReworkSupply;
import com.car.wash.entity.Supply;
import com.car.wash.entity.WashOrder;
import com.car.wash.enums.BayState;
import com.car.wash.enums.ReworkState;
import com.car.wash.enums.SupplyState;
import com.car.wash.enums.WashState;
import com.car.wash.repository.BayRepository;
import com.car.wash.repository.ReworkRepository;
import com.car.wash.repository.SupplyRepository;
import com.car.wash.repository.WashOrderRepository;
import com.car.wash.service.ReworkService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 回炉二次领料 · 仓管口径端到端：
 * 1. 挂回炉同时扣泡沫、毛巾，货架数字真减；
 * 2. 任一样不够，整笔领料失败、回炉挂不上，库存一件不动；
 * 3. 验收不过作废，料整笔退回货架；
 * 4. 验收通过，库存不再加回；
 * 5. 领料必须正好是泡沫和毛巾各一行。
 */
@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration")
class ReworkSupplyFlowTest {

    @Autowired ReworkService reworkService;
    @Autowired ReworkRepository reworks;
    @Autowired SupplyRepository supplies;
    @Autowired BayRepository bays;
    @Autowired WashOrderRepository orders;

    Long foamId;
    Long towelId;
    Long orderId;

    @BeforeEach
    void seed() {
        reworks.deleteAll();
        orders.deleteAll();
        supplies.deleteAll();
        bays.deleteAll();

        Bay bay = new Bay();
        bay.bayCode = "B-01";
        bay.bayName = "标准洗车位 1";
        bay.seatCount = 1;
        bay.bayState = BayState.空闲;
        bay = bays.save(bay);

        WashOrder o = new WashOrder();
        o.orderNo = "WO-T" + System.nanoTime();
        o.plateNo = "京T00001";
        o.bayId = bay.id;
        o.serviceType = "标准洗车";
        o.price = 30;
        o.orderDate = LocalDate.now();
        o.washState = WashState.已完成;
        orderId = orders.save(o).id;

        foamId = saveSupply("SP-F", "洗车泡沫", "瓶", 15, 5).id;
        towelId = saveSupply("SP-T", "超细纤维毛巾", "条", 40, 50).id;
    }

    private Supply saveSupply(String code, String name, String unit, int stock, int warn) {
        Supply s = new Supply();
        s.supplyCode = code + System.nanoTime();
        s.supplyName = name;
        s.unitText = unit;
        s.stock = stock;
        s.warnLine = warn;
        s.supplyState = stock == 0 ? SupplyState.已用完 : (stock < warn ? SupplyState.不足 : SupplyState.正常);
        return supplies.save(s);
    }

    private Rework hangForm(int foamQty, int towelQty) {
        Rework form = new Rework();
        form.orderId = orderId;
        form.reason = "后门水痕";
        form.supplyLines = List.of(
                line(foamId, foamQty),
                line(towelId, towelQty));
        return form;
    }

    private ReworkSupply line(Long supplyId, int qty) {
        ReworkSupply l = new ReworkSupply();
        l.supplyId = supplyId;
        l.quantity = qty;
        return l;
    }

    private int stock(Long id) {
        return supplies.findById(id).orElseThrow().stock;
    }

    @Test
    void hang_deducts_both_supplies_and_persists_lines() {
        Rework rw = reworkService.create(hangForm(2, 3));

        assertEquals(ReworkState.待回炉, rw.reworkState);
        assertEquals(13, stock(foamId), "泡沫 15 - 2 = 13，货架得真减");
        assertEquals(37, stock(towelId), "毛巾 40 - 3 = 37，货架得真减");
        List<ReworkSupply> lines = reworkService.list(null, null).stream()
                .filter(r -> r.id.equals(rw.id)).findFirst().orElseThrow().supplyLines;
        assertEquals(2, lines.size());
        assertTrue(lines.stream().noneMatch(l -> Boolean.TRUE.equals(l.returned)));
    }

    @Test
    void insufficient_stock_fails_whole_draw_and_does_not_hang() {
        long beforeCount = reworks.count();

        BizException ex = assertThrows(BizException.class,
                () -> reworkService.create(hangForm(999, 1)));
        assertTrue(ex.getMessage().contains("整笔领料失败"), ex.getMessage());

        // 整笔失败：回炉挂不上
        assertEquals(beforeCount, reworks.count());
        // 毛巾那行也一件没扣（不允许扣一半）
        assertEquals(15, stock(foamId));
        assertEquals(40, stock(towelId));
    }

    @Test
    void short_towel_also_fails_everything_atomically() {
        // 泡沫够、毛巾见底：照样整笔失败，泡沫不动
        Supply towel = supplies.findById(towelId).orElseThrow();
        towel.stock = 0;
        towel.supplyState = SupplyState.已用完;
        supplies.save(towel);

        assertThrows(BizException.class, () -> reworkService.create(hangForm(1, 1)));
        assertEquals(15, stock(foamId), "毛巾不够时泡沫也不能被扣走");
    }

    @Test
    void abort_returns_both_lines_to_shelf() {
        Rework rw = reworkService.create(hangForm(2, 3));
        rw = reworkService.advance(rw.id); // 待回炉 → 回炉中
        assertEquals(ReworkState.回炉中, rw.reworkState);

        // 客人仍不满意：验收不过作废，二次领料整笔退回货架
        Rework aborted = reworkService.abort(rw.id);
        assertEquals(ReworkState.已作废, aborted.reworkState);
        assertEquals(15, stock(foamId), "作废退料，泡沫加回到 15");
        assertEquals(40, stock(towelId), "作废退料，毛巾加回到 40");
        assertTrue(aborted.supplyLines.stream().allMatch(l -> Boolean.TRUE.equals(l.returned)));

        // 作废后原单不再占「未结」名额，可以重新挂
        assertTrue(reworkService.openOfOrder(orderId).isEmpty());
    }

    @Test
    void accepted_rework_keeps_stock_consumed() {
        Rework rw = reworkService.create(hangForm(1, 2));
        reworkService.advance(rw.id); // 回炉中
        Rework done = reworkService.advance(rw.id); // 已验收

        assertEquals(ReworkState.已验收, done.reworkState);
        assertEquals(14, stock(foamId), "验收通过，泡沫不退：15 - 1 = 14");
        assertEquals(38, stock(towelId), "验收通过，毛巾不退：40 - 2 = 38");
        assertThrows(BizException.class, () -> reworkService.abort(rw.id), "验收过的不许作废退料");
    }

    @Test
    void draw_must_be_foam_and_towel_only() {
        Long waxId = saveSupply("SP-W", "水晶蜡", "瓶", 10, 3).id;

        Rework form = hangForm(1, 1);
        form.supplyLines = List.of(line(foamId, 1), line(waxId, 1));
        BizException ex = assertThrows(BizException.class, () -> reworkService.create(form));
        assertTrue(ex.getMessage().contains("毛巾"), "把毛巾换成蜡，得提示缺毛巾：" + ex.getMessage());

        Rework onlyFoam = new Rework();
        onlyFoam.orderId = orderId;
        onlyFoam.supplyLines = List.of(line(foamId, 1));
        assertThrows(BizException.class, () -> reworkService.create(onlyFoam));

        // 被拦下来，库存没动
        assertEquals(15, stock(foamId));
        assertEquals(40, stock(towelId));
    }
}

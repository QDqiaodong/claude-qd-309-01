package com.car.wash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.car.wash.dto.BizException;
import com.car.wash.entity.Bay;
import com.car.wash.entity.CardFlow;
import com.car.wash.entity.MemberCard;
import com.car.wash.entity.WashOrder;
import com.car.wash.enums.BayState;
import com.car.wash.enums.CardState;
import com.car.wash.enums.FlowType;
import com.car.wash.enums.PayState;
import com.car.wash.enums.WashState;
import com.car.wash.repository.BayRepository;
import com.car.wash.repository.CardFlowRepository;
import com.car.wash.repository.MemberCardRepository;
import com.car.wash.repository.WashOrderRepository;
import com.car.wash.service.WashOrderService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 会员卡 ↔ 洗车单 · 店长口径端到端：
 * 1. 刷卡钉在这一张单上，金额跟单上写明的价一致才动余额，落一行扣款流水；
 * 2. 这单已扣过一笔，再刷当场挡下，卡上数字不再少；
 * 3. 单号没钉上一分钱不许动（不许先刷后补单）；
 * 4. 停卡、余额不够，整笔都动不了，不许先扣一半；
 * 5. 整笔撤单，已扣的那笔原数退回原卡，卡上余额归位，退款流水钉在同一单上；
 * 6. 晚班对账：从卡流水找得到单，从单找得到是哪一笔扣的。
 */
@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration")
class CardPaymentFlowTest {

    @Autowired WashOrderService orderService;
    @Autowired WashOrderRepository orders;
    @Autowired MemberCardRepository cards;
    @Autowired CardFlowRepository flows;
    @Autowired BayRepository bays;

    Long bayId;
    Long orderId;   // 30 元已完成单
    Long cardId;    // 余额 100 的正常卡

    @BeforeEach
    void seed() {
        flows.deleteAll();
        orders.deleteAll();
        cards.deleteAll();
        bays.deleteAll();

        Bay bay = new Bay();
        bay.bayCode = "B-01";
        bay.bayName = "标准洗车位 1";
        bay.seatCount = 5;
        bay.bayState = BayState.空闲;
        bayId = bays.save(bay).id;

        orderId = newOrder("WO-T" + System.nanoTime(), 30, WashState.已完成).id;
        cardId = newCard("MC-T" + System.nanoTime(), 100, CardState.正常).id;
    }

    private WashOrder newOrder(String no, int price, WashState state) {
        WashOrder o = new WashOrder();
        o.orderNo = no;
        o.plateNo = "京T00001";
        o.bayId = bayId;
        o.serviceType = "标准洗车";
        o.price = price;
        o.orderDate = LocalDate.now();
        o.washState = state;
        o.payState = PayState.未支付;
        return orders.save(o);
    }

    private MemberCard newCard(String no, int balance, CardState state) {
        MemberCard c = new MemberCard();
        c.cardNo = no;
        c.holderName = "测试卡";
        c.balance = balance;
        c.cardState = state;
        return cards.save(c);
    }

    private int balance(Long id) {
        return cards.findById(id).orElseThrow().balance;
    }

    @Test
    void pay_pins_charge_to_order_and_writes_flow() {
        CardFlow flow = orderService.payOrder(orderId, cardId, null);

        assertEquals(70, balance(cardId), "100 - 30 = 70，卡上真扣");
        assertEquals(FlowType.扣款, flow.flowType);
        assertEquals(orderId, flow.orderId, "流水得钉在这张单上");
        assertEquals(cardId, flow.cardId, "流水得钉在这张卡上");
        assertEquals(30, flow.amount);
        assertEquals(70, flow.balanceAfter, "流水得记下这笔之后的卡余额，晚班对账靠它");
        assertNotNull(flow.flowNo);
        assertNotNull(flow.createdAt);

        WashOrder order = orders.findById(orderId).orElseThrow();
        assertEquals(PayState.已支付, order.payState);
        assertEquals(flow.id, order.payFlowId, "单上得记下是哪一笔扣的");
    }

    @Test
    void amount_must_match_order_price() {
        BizException ex = assertThrows(BizException.class,
                () -> orderService.payOrder(orderId, cardId, 50));
        assertTrue(ex.getMessage().contains("对不上"), ex.getMessage());

        assertEquals(100, balance(cardId), "金额对不上，卡上一分不动");
        assertEquals(0, flows.count(), "被拦的刷卡不许留流水");
        assertEquals(PayState.未支付, orders.findById(orderId).orElseThrow().payState);
    }

    @Test
    void second_swipe_is_blocked_and_balance_untouched() {
        orderService.payOrder(orderId, cardId, null);
        assertEquals(70, balance(cardId));

        BizException ex = assertThrows(BizException.class,
                () -> orderService.payOrder(orderId, cardId, null));
        assertTrue(ex.getMessage().contains("已经扣过一笔"), ex.getMessage());

        assertEquals(70, balance(cardId), "再刷必须挡下，卡上数字不许再少");
        assertEquals(1, flows.count(), "成功扣款只有一笔流水");
    }

    @Test
    void no_order_no_balance_moves() {
        // 收银想先刷卡再补单号：单号没钉上就不许动余额
        assertThrows(BizException.class, () -> orderService.payOrder(null, cardId, 30));
        assertThrows(BizException.class, () -> orderService.payOrder(999999L, cardId, 30));

        assertEquals(100, balance(cardId), "先扣后补会留下对不上的流水，一律不许动");
        assertEquals(0, flows.count());
    }

    @Test
    void stopped_card_cannot_pay_at_all() {
        MemberCard stopped = cards.findById(cardId).orElseThrow();
        stopped.cardState = CardState.已停卡;
        cards.save(stopped);

        assertThrows(BizException.class, () -> orderService.payOrder(orderId, cardId, null));
        assertEquals(100, balance(cardId), "停卡整笔动不了");
        assertEquals(0, flows.count());
    }

    @Test
    void insufficient_balance_blocks_whole_charge() {
        MemberCard poor = cards.findById(cardId).orElseThrow();
        poor.balance = 20;
        cards.save(poor);

        assertThrows(BizException.class, () -> orderService.payOrder(orderId, cardId, null));
        assertEquals(20, balance(cardId), "余额不够整笔拦下，不许先扣一半");
        assertEquals(0, flows.count());
        assertEquals(PayState.未支付, orders.findById(orderId).orElseThrow().payState);
    }

    @Test
    void void_order_refunds_original_card_and_restores_balance() {
        CardFlow charge = orderService.payOrder(orderId, cardId, null);
        assertEquals(70, balance(cardId));

        WashOrder voided = orderService.voidOrder(orderId);

        assertEquals(WashState.已撤销, voided.washState);
        assertEquals(PayState.已退款, voided.payState);
        assertEquals(100, balance(cardId), "已扣的那笔原数退回，卡上余额归位");

        List<CardFlow> all = flows.findAllByOrderByIdAsc();
        assertEquals(2, all.size(), "一扣一退两行流水");
        CardFlow refund = all.get(1);
        assertEquals(FlowType.退款, refund.flowType);
        assertEquals(orderId, refund.orderId, "退款流水也钉在这张单上");
        assertEquals(cardId, refund.cardId, "钱退回原来扣的那张卡");
        assertEquals(30, refund.amount);
        assertEquals(100, refund.balanceAfter, "退回后的余额记在流水上");
        assertEquals(charge.id, refund.refFlowId, "退款指回原扣款，对得上是哪一笔");
    }

    @Test
    void void_unpaid_order_leaves_card_alone() {
        WashOrder voided = orderService.voidOrder(orderId);

        assertEquals(WashState.已撤销, voided.washState);
        assertEquals(PayState.未支付, voided.payState);
        assertEquals(100, balance(cardId), "没刷过卡的单撤掉不动卡");
        assertEquals(0, flows.count());
    }

    @Test
    void voided_order_is_final() {
        orderService.payOrder(orderId, cardId, null);
        orderService.voidOrder(orderId);

        assertThrows(BizException.class, () -> orderService.voidOrder(orderId), "撤过的单不许再撤");
        assertThrows(BizException.class, () -> orderService.payOrder(orderId, cardId, null), "撤过的单不许再刷");

        WashOrder touch = new WashOrder();
        touch.id = orderId;
        touch.price = 99;
        assertThrows(BizException.class, () -> orderService.save(touch), "撤过的单不许再改");

        assertEquals(100, balance(cardId), "撤过之后卡上数字不许再动");
        assertEquals(2, flows.count(), "流水就停在一扣一退");
    }

    @Test
    void paid_order_price_is_nailed() {
        orderService.payOrder(orderId, cardId, null);

        WashOrder touch = new WashOrder();
        touch.id = orderId;
        touch.price = 25;
        BizException ex = assertThrows(BizException.class, () -> orderService.save(touch));
        assertTrue(ex.getMessage().contains("不许再改金额"), ex.getMessage());

        assertEquals(30, orders.findById(orderId).orElseThrow().price, "已扣款的单金额钉死在流水上");
    }

    @Test
    void reconciliation_matches_both_directions() {
        // 晚班对账：从卡流水找单、从单找流水，两头是同一笔
        orderService.payOrder(orderId, cardId, null);

        CardFlow flow = flows.findAllByOrderByIdAsc().get(0);
        WashOrder byOrder = orders.findById(flow.orderId).orElseThrow();
        assertEquals(orderId, byOrder.id);
        assertEquals(flow.id, byOrder.payFlowId, "单上记的正是这笔流水");
        assertEquals(flow.amount, byOrder.price, "流水金额跟单上写明的价一致");
        assertEquals(flow.balanceAfter, balance(cardId), "流水余额跟卡上现数一致");
    }

    @Test
    void refund_goes_back_even_when_card_stopped_later() {
        orderService.payOrder(orderId, cardId, null);
        // 扣完款卡被停了：退款照样退得回来，退款不是扣款
        MemberCard c = cards.findById(cardId).orElseThrow();
        c.cardState = CardState.已停卡;
        cards.save(c);

        orderService.voidOrder(orderId);

        MemberCard after = cards.findById(cardId).orElseThrow();
        assertEquals(100, after.balance, "停卡也照退，余额归位");
        assertEquals(CardState.已停卡, after.cardState, "退款只归位余额，不替人改卡状态");
    }

    @Test
    void unpaid_order_has_no_flow_id() {
        WashOrder order = orders.findById(orderId).orElseThrow();
        assertEquals(PayState.未支付, order.payState);
        assertNull(order.payFlowId);
    }
}

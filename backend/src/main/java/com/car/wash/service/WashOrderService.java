package com.car.wash.service;

import com.car.wash.dto.BizException;
import com.car.wash.entity.Bay;
import com.car.wash.entity.CardFlow;
import com.car.wash.entity.MemberCard;
import com.car.wash.entity.Rework;
import com.car.wash.entity.WashOrder;
import com.car.wash.enums.FlowType;
import com.car.wash.enums.PayState;
import com.car.wash.enums.WashState;
import com.car.wash.repository.BayRepository;
import com.car.wash.repository.CardFlowRepository;
import com.car.wash.repository.MemberCardRepository;
import com.car.wash.repository.WashOrderRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 洗车单：状态只能顺着走，工位停用 / 满位就开不了单；
 * 已完成的单子只要还挂着未验收回炉，就不能退回待洗或清洗中，列表和详情带「回炉未结」标。
 *
 * 卡账口径（按店长定的）：
 * 1. 刷卡必须钉在这一张洗车单上，金额跟单上写明的价一致才许动余额，单号没钉上一分钱不许动；
 * 2. 这单已经记过一笔成功扣款，再刷当场挡下，卡上数字不再少；
 * 3. 停卡、余额不够，整笔都动不了，不许先扣一半；
 * 4. 单子整笔撤掉，已扣的那笔原数退回原卡，卡上余额归位；
 * 5. 每动一次余额都落一行卡流水（钉卡、钉单、钉这笔之后的余额），单上也记下是哪一笔扣的，
 *    晚班对账从卡流水找单、从单找流水，两头对得上。
 */
@Service
public class WashOrderService {

    private final WashOrderRepository orders;
    private final BayRepository bays;
    private final MemberCardRepository cards;
    private final CardFlowRepository flows;
    private final BayOccupancyService occupancy;
    private final ReworkService reworks;

    public WashOrderService(WashOrderRepository orders, BayRepository bays, MemberCardRepository cards,
                            CardFlowRepository flows, BayOccupancyService occupancy, ReworkService reworks) {
        this.orders = orders;
        this.bays = bays;
        this.cards = cards;
        this.flows = flows;
        this.occupancy = occupancy;
        this.reworks = reworks;
    }

    public List<WashOrder> list(WashState state, String keyword) {
        List<WashOrder> rows = orders.findAllByOrderByIdAsc();
        Map<Long, Rework> openMap = reworks.openByOrderIds(rows.stream().map(o -> o.id).toList());
        Map<Long, CardFlow> payFlowMap = flows.findAllById(
                rows.stream().map(o -> o.payFlowId).filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(f -> f.id, Function.identity()));
        Map<Long, MemberCard> cardMap = cards.findAllById(
                payFlowMap.values().stream().map(f -> f.cardId).distinct().toList()).stream()
                .collect(Collectors.toMap(c -> c.id, Function.identity()));
        rows.forEach(o -> {
            Rework rw = openMap.get(o.id);
            o.reworkOpen = rw != null;
            o.openReworkNo = rw == null ? null : rw.reworkNo;
            CardFlow payFlow = o.payFlowId == null ? null : payFlowMap.get(o.payFlowId);
            o.payFlowNo = payFlow == null ? null : payFlow.flowNo;
            MemberCard card = payFlow == null ? null : cardMap.get(payFlow.cardId);
            o.payCardNo = card == null ? null : card.cardNo;
        });
        return rows.stream()
                .filter(o -> state == null || state == o.washState)
                .filter(o -> keyword == null || keyword.isBlank()
                        || o.orderNo.contains(keyword) || o.plateNo.contains(keyword))
                .sorted((a, b) -> b.id.compareTo(a.id))
                .toList();
    }

    @Transactional
    public WashOrder save(WashOrder form) {
        WashOrder existed = null;
        if (form.id != null) {
            existed = orders.findById(form.id).orElseThrow(() -> new BizException("洗车单不存在"));
            if (form.orderNo == null || form.orderNo.isBlank()) {
                form.orderNo = existed.orderNo;
            }
            if (form.plateNo == null || form.plateNo.isBlank()) {
                form.plateNo = existed.plateNo;
            }
        }
        if (form.orderNo == null || form.plateNo == null) {
            throw new BizException("单号和车牌都得填");
        }
        orders.findByOrderNo(form.orderNo).ifPresent(o -> {
            if (!o.id.equals(form.id)) {
                throw new BizException("单号 " + form.orderNo + " 重复了");
            }
        });
        if (form.price != null && form.price < 0) {
            throw new BizException("金额不能是负数");
        }

        if (existed != null) {
            if (existed.washState == WashState.已撤销) {
                throw new BizException("单子「" + existed.orderNo + "」已经整笔撤掉，卡账也了清了，不许再改");
            }
            // 已刷过卡的单，金额钉死在流水上；要改价先整笔撤掉退款，再重开。
            if (existed.payState == PayState.已支付 && form.price != null
                    && !form.price.equals(existed.price)) {
                throw new BizException("单子「" + existed.orderNo + "」已经刷卡结账（流水钉死了 "
                        + existed.price + " 元），不许再改金额；要改先整笔撤掉退款");
            }
            // 已完成且还挂着未验收回炉：原单必须钉在已完成，退回待洗/清洗中一律拦。
            boolean movingBack = form.washState != null && form.washState != existed.washState
                    && form.washState.ordinal() < existed.washState.ordinal();
            if (existed.washState == WashState.已完成 && movingBack
                    && !reworks.openOfOrder(existed.id).isEmpty()) {
                throw new BizException("原单「" + existed.orderNo + "」还挂着未验收的回炉（回炉未结），不能退回"
                        + form.washState);
            }
            if (form.washState != null && form.washState != existed.washState
                    && !existed.washState.canMoveTo(form.washState)) {
                throw new BizException("洗车单得按「待洗 → 清洗中 → 已完成」走，不能跳步也不能倒回去；"
                        + "整笔撤单走「撤单」，会连同卡账一起了");
            }
            // 换工位（且单子还没洗完）才需要重新看停用/满位；留在原工位不重复计数。
            if (form.bayId != null && !form.bayId.equals(existed.bayId)
                    && (form.washState == null ? existed.washState : form.washState) != WashState.已完成) {
                Bay bay = occupancy.requireBay(form.bayId);
                occupancy.requireCanAccept(bay, "这单");
                existed.bayId = form.bayId;
            } else if (form.bayId != null) {
                existed.bayId = form.bayId;
            }
            if (form.serviceType != null && !form.serviceType.isBlank()) {
                existed.serviceType = form.serviceType;
            }
            if (form.price != null) {
                existed.price = form.price;
            }
            if (form.washState != null) {
                existed.washState = form.washState;
            }
            return orders.save(existed);
        }

        // 新单：必须排进一个没停用、还没满的工位（停用和满位都当场说清原因）。
        if (form.bayId == null) {
            throw new BizException("开单得先排个工位");
        }
        Bay bay = occupancy.requireBay(form.bayId);
        occupancy.requireCanAccept(bay, "新单");
        form.orderDate = form.orderDate == null ? LocalDate.now() : form.orderDate;
        form.washState = form.washState == null ? WashState.待洗 : form.washState;
        form.payState = PayState.未支付;
        return orders.save(form);
    }

    /**
     * 刷卡结账：扣款钉在这一张洗车单上。
     * 单号没钉上（单不存在）、刷的金额跟单上写明的价对不上、这单已经扣过一笔，
     * 都当场挡下，卡上数字一分不动；停卡、余额不够同样整笔动不了。
     * 成功才落一行扣款流水，单上记下是哪一笔扣的。
     */
    @Transactional
    public CardFlow payOrder(Long orderId, Long cardId, Integer amount) {
        if (orderId == null) {
            throw new BizException("单号没钉上就不许动余额：先说清刷的是哪张洗车单");
        }
        WashOrder order = orders.findById(orderId)
                .orElseThrow(() -> new BizException("洗车单不存在，单号没钉上不许动余额"));
        if (order.washState == WashState.已撤销) {
            throw new BizException("单子「" + order.orderNo + "」已经整笔撤掉了，不许再刷卡");
        }
        int price = order.price == null ? 0 : order.price;
        if (price <= 0) {
            throw new BizException("单子「" + order.orderNo + "」还没写明价格，先把价写上再刷卡");
        }
        if (amount != null && amount != price) {
            throw new BizException("刷的 " + amount + " 元跟这单写明的 " + price + " 元对不上，不许动余额");
        }
        if (order.payState == PayState.已支付 && order.payFlowId != null) {
            String flowNo = flows.findById(order.payFlowId).map(f -> f.flowNo).orElse("?");
            throw new BizException("单子「" + order.orderNo + "」已经扣过一笔（流水 " + flowNo
                    + "），再刷也不会重复扣");
        }
        if (cardId == null) {
            throw new BizException("得说清刷哪张会员卡");
        }
        MemberCard card = cards.findById(cardId).orElseThrow(() -> new BizException("会员卡不存在"));
        // 停卡、余额不够在这里整笔拦下，卡上数字不动。
        card.pay(price);
        cards.save(card);

        CardFlow flow = newFlow(card, order, price, FlowType.扣款, null);
        flow = flows.save(flow);
        order.payState = PayState.已支付;
        order.payFlowId = flow.id;
        orders.save(order);
        return flow;
    }

    /**
     * 整笔撤单：单子作废成「已撤销」。
     * 这单要是刷过卡，已扣的那笔原数退回原来那张卡（停卡也照退），落一行退款流水钉在同一单上，
     * 卡上余额按退回后的数归位；没刷过卡的单撤掉不动卡。
     */
    @Transactional
    public WashOrder voidOrder(Long id) {
        WashOrder order = orders.findById(id).orElseThrow(() -> new BizException("洗车单不存在"));
        if (order.washState == WashState.已撤销) {
            throw new BizException("单子「" + order.orderNo + "」已经撤过了，卡账早就了清");
        }
        if (!reworks.openOfOrder(order.id).isEmpty()) {
            throw new BizException("单子「" + order.orderNo + "」还挂着未验收的回炉，先了回炉再撤单");
        }
        if (order.payState == PayState.已支付 && order.payFlowId != null) {
            CardFlow charge = flows.findById(order.payFlowId)
                    .orElseThrow(() -> new BizException("这单的扣款流水找不到了，账对不上，先别撤"));
            MemberCard card = cards.findById(charge.cardId)
                    .orElseThrow(() -> new BizException("当时扣款的那张卡找不到了，退不回去，先别撤"));
            card.refund(charge.amount);
            cards.save(card);
            flows.save(newFlow(card, order, charge.amount, FlowType.退款, charge.id));
            order.payState = PayState.已退款;
        }
        order.washState = WashState.已撤销;
        return orders.save(order);
    }

    /** 落一行卡流水：钉卡、钉单、钉这笔之后的卡余额。 */
    private CardFlow newFlow(MemberCard card, WashOrder order, Integer amount, FlowType type, Long refFlowId) {
        CardFlow flow = new CardFlow();
        flow.flowNo = nextFlowNo();
        flow.cardId = card.id;
        flow.orderId = order.id;
        flow.amount = amount;
        flow.flowType = type;
        flow.balanceAfter = card.balance;
        flow.refFlowId = refFlowId;
        flow.createdAt = LocalDateTime.now();
        return flow;
    }

    private String nextFlowNo() {
        for (long i = flows.count() + 1; ; i++) {
            String no = String.format("CF-%02d", i);
            if (!flows.existsByFlowNo(no)) {
                return no;
            }
        }
    }
}

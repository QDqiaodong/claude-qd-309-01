package com.car.wash.service;

import com.car.wash.dto.BizException;
import com.car.wash.entity.CardFlow;
import com.car.wash.entity.MemberCard;
import com.car.wash.entity.WashOrder;
import com.car.wash.enums.CardState;
import com.car.wash.repository.CardFlowRepository;
import com.car.wash.repository.MemberCardRepository;
import com.car.wash.repository.WashOrderRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberCardService {

    private final MemberCardRepository cards;
    private final CardFlowRepository flows;
    private final WashOrderRepository orders;

    public MemberCardService(MemberCardRepository cards, CardFlowRepository flows,
                             WashOrderRepository orders) {
        this.cards = cards;
        this.flows = flows;
        this.orders = orders;
    }

    public List<MemberCard> list(CardState state, String keyword) {
        return cards.findAllByOrderByIdAsc().stream()
                .filter(c -> state == null || state == c.cardState)
                .filter(c -> keyword == null || keyword.isBlank()
                        || c.cardNo.contains(keyword) || c.holderName.contains(keyword))
                .toList();
    }

    /**
     * 卡流水（晚班对账用）：每行都钉着卡号、单号和这笔之后的卡余额，
     * 从卡流水能直接找到是哪张洗车单动的钱。
     */
    public List<CardFlow> flows(Long cardId) {
        List<CardFlow> rows = cardId == null
                ? flows.findAllByOrderByIdAsc()
                : flows.findByCardIdOrderByIdDesc(cardId);
        Map<Long, MemberCard> cardMap = cards.findAllById(
                rows.stream().map(f -> f.cardId).distinct().toList()).stream()
                .collect(Collectors.toMap(c -> c.id, Function.identity()));
        Map<Long, WashOrder> orderMap = orders.findAllById(
                rows.stream().map(f -> f.orderId).distinct().toList()).stream()
                .collect(Collectors.toMap(o -> o.id, Function.identity()));
        rows.forEach(f -> {
            MemberCard card = cardMap.get(f.cardId);
            if (card != null) {
                f.cardNo = card.cardNo;
                f.holderName = card.holderName;
            }
            WashOrder order = orderMap.get(f.orderId);
            f.orderNo = order == null ? null : order.orderNo;
        });
        return rows.stream().sorted((a, b) -> b.id.compareTo(a.id)).toList();
    }

    @Transactional
    public MemberCard save(MemberCard form) {
        MemberCard existed = null;
        if (form.id != null) {
            existed = cards.findById(form.id).orElseThrow(() -> new BizException("会员卡不存在"));
            if (form.cardNo == null || form.cardNo.isBlank()) {
                form.cardNo = existed.cardNo;
            }
            if (form.holderName == null || form.holderName.isBlank()) {
                form.holderName = existed.holderName;
            }
        }
        if (form.cardNo == null || form.holderName == null) {
            throw new BizException("卡号和持卡人姓名都得填");
        }
        cards.findByCardNo(form.cardNo).ifPresent(o -> {
            if (!o.id.equals(form.id)) {
                throw new BizException("卡号 " + form.cardNo + " 重复了");
            }
        });
        if (form.phone != null && !form.phone.isBlank() && !form.phone.matches("\\d{11}")) {
            throw new BizException("手机号得是 11 位数字");
        }
        if (form.balance != null && form.balance < 0) {
            throw new BizException("余额不能是负数");
        }
        if (existed == null) {
            if (form.balance == null) {
                form.balance = 0;
            }
            form.cardState = form.cardState == null ? CardState.正常 : form.cardState;
            return cards.save(form);
        }
        if (form.phone != null && !form.phone.isBlank()) {
            existed.phone = form.phone;
        }
        if (form.cardLevel != null && !form.cardLevel.isBlank()) {
            existed.cardLevel = form.cardLevel;
        }
        if (form.balance != null) {
            existed.balance = form.balance;
        }
        if (form.cardState != null) {
            existed.cardState = form.cardState;
        }
        existed.cardNo = form.cardNo;
        existed.holderName = form.holderName;
        return cards.save(existed);
    }
}

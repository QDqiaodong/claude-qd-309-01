package com.car.wash.controller;

import com.car.wash.entity.CardFlow;
import com.car.wash.entity.WashOrder;
import com.car.wash.enums.WashState;
import com.car.wash.service.WashOrderService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class WashOrderController {

    private final WashOrderService service;

    public WashOrderController(WashOrderService service) {
        this.service = service;
    }

    @GetMapping
    public List<WashOrder> list(@RequestParam(required = false) String state,
                                @RequestParam(required = false) String keyword) {
        return service.list(state == null || state.isBlank() ? null : WashState.valueOf(state), keyword);
    }

    @PostMapping
    public WashOrder create(@RequestBody WashOrder form) {
        return service.save(form);
    }

    @PutMapping("/{id}")
    public WashOrder update(@PathVariable Long id, @RequestBody WashOrder form) {
        form.id = id;
        return service.save(form);
    }

    /**
     * 刷卡结账：扣款钉在这张单上，金额按单上写明的价走；
     * body 里带了 amount 就必须跟单价一致，对不上当场拦。
     */
    @PostMapping("/{id}/pay")
    public CardFlow pay(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object cardId = body.get("cardId");
        Object amount = body.get("amount");
        return service.payOrder(id,
                cardId == null ? null : Long.valueOf(String.valueOf(cardId)),
                amount == null ? null : Integer.valueOf(String.valueOf(amount)));
    }

    /** 整笔撤单：刷过卡的会把那笔原数退回原卡。 */
    @PostMapping("/{id}/void")
    public WashOrder voidOrder(@PathVariable Long id) {
        return service.voidOrder(id);
    }
}

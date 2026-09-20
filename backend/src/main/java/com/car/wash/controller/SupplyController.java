package com.car.wash.controller;

import com.car.wash.entity.Supply;
import com.car.wash.enums.SupplyState;
import com.car.wash.service.SupplyService;
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
@RequestMapping("/api/supplies")
public class SupplyController {

    private final SupplyService service;

    public SupplyController(SupplyService service) {
        this.service = service;
    }

    @GetMapping
    public List<Supply> list(@RequestParam(required = false) String state,
                             @RequestParam(required = false) String keyword) {
        return service.list(state == null || state.isBlank() ? null : SupplyState.valueOf(state), keyword);
    }

    @PostMapping
    public Supply create(@RequestBody Supply form) {
        return service.save(form);
    }

    @PutMapping("/{id}")
    public Supply update(@PathVariable Long id, @RequestBody Supply form) {
        form.id = id;
        return service.save(form);
    }

    /** 用掉几件。 */
    @PostMapping("/{id}/consume")
    public Supply consume(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return service.consume(id, body == null ? Map.of() : body);
    }
}

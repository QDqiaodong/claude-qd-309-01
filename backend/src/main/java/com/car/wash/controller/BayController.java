package com.car.wash.controller;

import com.car.wash.entity.Bay;
import com.car.wash.enums.BayState;
import com.car.wash.service.BayService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bays")
public class BayController {

    private final BayService service;

    public BayController(BayService service) {
        this.service = service;
    }

    @GetMapping
    public List<Bay> list(@RequestParam(required = false) String state,
                          @RequestParam(required = false) String keyword) {
        return service.list(state == null || state.isBlank() ? null : BayState.valueOf(state), keyword);
    }

    @PostMapping
    public Bay create(@RequestBody Bay form) {
        return service.save(form);
    }

    @PutMapping("/{id}")
    public Bay update(@PathVariable Long id, @RequestBody Bay form) {
        form.id = id;
        return service.save(form);
    }
}

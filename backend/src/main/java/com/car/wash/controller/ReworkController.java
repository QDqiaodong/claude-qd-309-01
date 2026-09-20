package com.car.wash.controller;

import com.car.wash.entity.Rework;
import com.car.wash.enums.ReworkState;
import com.car.wash.service.ReworkService;
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
@RequestMapping("/api/reworks")
public class ReworkController {

    private final ReworkService service;

    public ReworkController(ReworkService service) {
        this.service = service;
    }

    /** 回炉名单。 */
    @GetMapping
    public List<Rework> list(@RequestParam(required = false) String state,
                             @RequestParam(required = false) String keyword) {
        return service.list(state == null || state.isBlank() ? null : ReworkState.valueOf(state), keyword);
    }

    /** 挂回炉：body 带 orderId、bayId（不传就认原工位）、reason、supplyLines（泡沫、毛巾各一行写清件数）。 */
    @PostMapping
    public Rework create(@RequestBody Rework form) {
        return service.create(form);
    }

    /** 推进：待回炉 → 回炉中 → 已验收。 */
    @PostMapping("/{id}/advance")
    public Rework advance(@PathVariable Long id) {
        return service.advance(id);
    }

    /**
     * 客人做到一半仍不满意、验收不过：作废回炉，
     * 二次领料（泡沫、毛巾）整笔退回货架；已验收的不退、不许作废。
     */
    @PostMapping("/{id}/abort")
    public Rework abort(@PathVariable Long id) {
        return service.abort(id);
    }

    /** 改派到别的空闲工位。 */
    @PutMapping("/{id}/bay")
    public Rework reassign(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object bayId = body.get("bayId");
        if (bayId == null) {
            throw new com.car.wash.dto.BizException("改派得选一个空闲工位");
        }
        return service.reassign(id, Long.valueOf(String.valueOf(bayId)));
    }
}

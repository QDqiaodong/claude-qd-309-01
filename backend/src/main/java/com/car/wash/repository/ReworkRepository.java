package com.car.wash.repository;

import com.car.wash.entity.Rework;
import com.car.wash.enums.ReworkState;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReworkRepository extends JpaRepository<Rework, Long> {

    Optional<Rework> findByReworkNo(String reworkNo);

    List<Rework> findAllByOrderByIdAsc();

    /** 原单上还挂着未验收（待回炉 / 回炉中）的回炉。 */
    List<Rework> findByOrderIdAndReworkStateIn(Long orderId, List<ReworkState> states);

    List<Rework> findByReworkStateIn(List<ReworkState> states);

    /** 工位上正在回炉中的车，算这个工位的占用。 */
    List<Rework> findByBayIdAndReworkState(Long bayId, ReworkState state);

    boolean existsByReworkNo(String reworkNo);
}

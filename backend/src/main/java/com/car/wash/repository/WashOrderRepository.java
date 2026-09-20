package com.car.wash.repository;

import com.car.wash.entity.WashOrder;
import com.car.wash.enums.WashState;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WashOrderRepository extends JpaRepository<WashOrder, Long> {

    Optional<WashOrder> findByOrderNo(String orderNo);

    List<WashOrder> findAllByOrderByIdAsc();

    /** 工位上还没洗完的单子（待洗 / 清洗中）——统一占用口径用这一条。 */
    long countByBayIdAndWashStateIn(Long bayId, Collection<WashState> states);
}

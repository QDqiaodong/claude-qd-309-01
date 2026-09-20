package com.car.wash.repository;

import com.car.wash.entity.Bay;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BayRepository extends JpaRepository<Bay, Long> {

    Optional<Bay> findByBayCode(String code);

    List<Bay> findAllByOrderByIdAsc();
}

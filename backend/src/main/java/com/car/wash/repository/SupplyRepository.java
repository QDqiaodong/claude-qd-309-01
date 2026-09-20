package com.car.wash.repository;

import com.car.wash.entity.Supply;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplyRepository extends JpaRepository<Supply, Long> {

    Optional<Supply> findBySupplyCode(String code);

    List<Supply> findAllByOrderByIdAsc();
}

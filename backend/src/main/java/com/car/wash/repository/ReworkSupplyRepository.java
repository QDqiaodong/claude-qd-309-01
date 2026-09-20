package com.car.wash.repository;

import com.car.wash.entity.ReworkSupply;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReworkSupplyRepository extends JpaRepository<ReworkSupply, Long> {

    List<ReworkSupply> findByReworkIdInOrderByIdAsc(List<Long> reworkIds);

    List<ReworkSupply> findByReworkIdOrderByIdAsc(Long reworkId);
}

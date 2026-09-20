package com.car.wash.repository;

import com.car.wash.entity.CardFlow;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardFlowRepository extends JpaRepository<CardFlow, Long> {

    List<CardFlow> findAllByOrderByIdAsc();

    List<CardFlow> findByCardIdOrderByIdDesc(Long cardId);

    List<CardFlow> findByOrderIdOrderByIdAsc(Long orderId);

    boolean existsByFlowNo(String flowNo);
}

package com.car.wash.repository;

import com.car.wash.entity.MemberCard;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberCardRepository extends JpaRepository<MemberCard, Long> {

    Optional<MemberCard> findByCardNo(String code);

    List<MemberCard> findAllByOrderByIdAsc();
}

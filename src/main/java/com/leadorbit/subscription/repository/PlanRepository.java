package com.leadorbit.subscription.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.leadorbit.subscription.domain.Plan;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    Optional<Plan> findByCodeAndDeletedAtIsNull(String code);

    Optional<Plan> findByIdAndDeletedAtIsNull(Long id);

    List<Plan> findAllByDeletedAtIsNullOrderByCodeAsc();

    @Query("from Plan p where p.deletedAt is null and p.active = true and p.id = :id")
    Optional<Plan> findActiveById(@Param("id") Long id);
}
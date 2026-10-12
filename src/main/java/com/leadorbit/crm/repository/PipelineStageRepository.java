package com.leadorbit.crm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.leadorbit.crm.domain.PipelineStage;

public interface PipelineStageRepository extends JpaRepository<PipelineStage, Long> {

    Optional<PipelineStage> findByCode(String code);

    boolean existsByCode(String code);

    Optional<PipelineStage> findByCodeAndActiveTrue(String code);

    List<PipelineStage> findAllByActiveTrueOrderBySortOrderAsc();

    List<PipelineStage> findAllByOrderBySortOrderAsc();
}
package com.leadorbit.catalog.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.leadorbit.catalog.domain.GstRate;

public interface GstRateRepository extends JpaRepository<GstRate, Long> {

    Optional<GstRate> findByCode(String code);

    boolean existsByCode(String code);

    List<GstRate> findAllByActiveTrueOrderByRateAsc();

    List<GstRate> findAllByOrderByRateAsc();

    /**
     * The most recent active rate applicable on {@code date} (highest effective_from).
     */
    Optional<GstRate> findFirstByActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
        LocalDate date);
}
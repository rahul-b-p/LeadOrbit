package com.leadorbit.crm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.leadorbit.crm.domain.LeadSource;

public interface LeadSourceRepository extends JpaRepository<LeadSource, Long> {

    Optional<LeadSource> findByCode(String code);

    boolean existsByCode(String code);

    List<LeadSource> findAllByActiveTrueOrderByNameAsc();

    List<LeadSource> findAllByOrderByNameAsc();
}
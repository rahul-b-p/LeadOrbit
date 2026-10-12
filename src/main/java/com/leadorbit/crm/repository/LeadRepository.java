package com.leadorbit.crm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.leadorbit.crm.domain.Lead;

public interface LeadRepository
    extends JpaRepository<Lead, Long>, JpaSpecificationExecutor<Lead> {

    Optional<Lead> findByIdAndDeletedAtIsNull(Long id);

    List<Lead> findAllByDeletedAtIsNullOrderByCreatedAtDesc();
}
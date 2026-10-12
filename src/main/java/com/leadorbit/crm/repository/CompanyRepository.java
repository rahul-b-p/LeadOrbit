package com.leadorbit.crm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.leadorbit.crm.domain.Company;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByNameAndDeletedAtIsNull(String name);

    List<Company> findAllByDeletedAtIsNullOrderByNameAsc();

    long countByDeletedAtIsNull();
}
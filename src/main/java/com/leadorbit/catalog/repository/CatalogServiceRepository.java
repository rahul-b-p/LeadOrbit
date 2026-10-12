package com.leadorbit.catalog.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.leadorbit.catalog.domain.CatalogService;

public interface CatalogServiceRepository extends JpaRepository<CatalogService, Long> {

    Optional<CatalogService> findByCodeAndDeletedAtIsNull(String code);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    Optional<CatalogService> findByIdAndDeletedAtIsNull(Long id);

    List<CatalogService> findAllByDeletedAtIsNullAndActiveTrueOrderByNameAsc();

    List<CatalogService> findAllByDeletedAtIsNullOrderByNameAsc();

    List<CatalogService> findAllByIdInAndDeletedAtIsNull(java.util.Collection<Long> ids);
}
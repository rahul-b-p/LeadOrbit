package com.leadorbit.billing.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.leadorbit.billing.domain.SellerProfile;

public interface SellerProfileRepository extends JpaRepository<SellerProfile, Long> {

    Optional<SellerProfile> findByIdAndDeletedAtIsNull(Long id);

    Optional<SellerProfile> findFirstByIsDefaultTrueAndDeletedAtIsNull();

    boolean existsByGstin(String gstin);

    List<SellerProfile> findAllByDeletedAtIsNullOrderByLegalNameAsc();
}
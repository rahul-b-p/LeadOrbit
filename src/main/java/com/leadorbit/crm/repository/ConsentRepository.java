package com.leadorbit.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.leadorbit.crm.domain.Consent;

public interface ConsentRepository extends JpaRepository<Consent, Long> {
}
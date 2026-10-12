package com.leadorbit.crm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.leadorbit.crm.domain.Contact;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    Optional<Contact> findByIdAndDeletedAtIsNull(Long id);

    List<Contact> findAllByDeletedAtIsNullOrderByNameAsc();

    List<Contact> findAllByCompanyIdAndDeletedAtIsNullOrderByNameAsc(Long companyId);

    boolean existsByEmailAndDeletedAtIsNull(String email);
}
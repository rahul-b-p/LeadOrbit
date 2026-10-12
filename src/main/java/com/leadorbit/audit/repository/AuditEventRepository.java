package com.leadorbit.audit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.leadorbit.audit.domain.AuditEvent;

public interface AuditEventRepository
    extends JpaRepository<AuditEvent, Long>, JpaSpecificationExecutor<AuditEvent> {
}

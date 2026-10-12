package com.leadorbit.catalog.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leadorbit.audit.AuditRecord;
import com.leadorbit.audit.AuditService;
import com.leadorbit.audit.domain.AuditOutcome;
import com.leadorbit.catalog.domain.CatalogService;
import com.leadorbit.catalog.domain.TaxClass;
import com.leadorbit.catalog.dto.ServiceRequest;
import com.leadorbit.catalog.dto.ServiceResponse;
import com.leadorbit.catalog.repository.CatalogServiceRepository;
import com.leadorbit.common.error.ApiException;
import com.leadorbit.security.SecurityUtils;

@Service
public class ServiceCatalog {

    private static final String ENTITY = "service";

    private final CatalogServiceRepository repository;
    private final AuditService auditService;

    public ServiceCatalog(CatalogServiceRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> list(boolean includeInactive) {
        return (includeInactive
            ? repository.findAllByDeletedAtIsNullOrderByNameAsc()
            : repository.findAllByDeletedAtIsNullAndActiveTrueOrderByNameAsc())
            .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ServiceResponse get(Long id) {
        return toResponse(requireService(id));
    }

    @Transactional
    public ServiceResponse create(ServiceRequest request) {
        String code = request.code().trim();
        if (repository.existsByCodeAndDeletedAtIsNull(code)) {
            throw ApiException.conflict("A service with this code already exists");
        }

        CatalogService service = new CatalogService();
        apply(service, request);
        repository.save(service);

        auditService.record(AuditRecord.builder()
            .action("catalog.service_create").outcome(AuditOutcome.SUCCESS)
            .actorUserId(SecurityUtils.currentUserId())
            .entityType(ENTITY).entityId(String.valueOf(service.getId()))
            .summary("Created service " + code)
            .after(toResponse(service))
            .build());

        return toResponse(service);
    }

    @Transactional
    public ServiceResponse update(Long id, ServiceRequest request) {
        CatalogService service = requireService(id);
        ServiceResponse before = toResponse(service);
        apply(service, request);
        repository.save(service);

        auditService.record(AuditRecord.builder()
            .action("catalog.service_update").outcome(AuditOutcome.SUCCESS)
            .actorUserId(SecurityUtils.currentUserId())
            .entityType(ENTITY).entityId(String.valueOf(id))
            .summary("Updated service " + service.getCode())
            .before(before).after(toResponse(service))
            .build());

        return toResponse(service);
    }

    @Transactional
    public void deactivate(Long id) {
        CatalogService service = requireService(id);
        ServiceResponse before = toResponse(service);
        service.setDeletedAt(Instant.now());
        service.setActive(false);
        repository.save(service);

        auditService.record(AuditRecord.builder()
            .action("catalog.service_deactivate").outcome(AuditOutcome.SUCCESS)
            .actorUserId(SecurityUtils.currentUserId())
            .entityType(ENTITY).entityId(String.valueOf(id))
            .summary("Deactivated service " + service.getCode())
            .before(before)
            .build());
    }

    private CatalogService requireService(Long id) {
        return repository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> ApiException.notFound("Service not found"));
    }

    private void apply(CatalogService service, ServiceRequest request) {
        service.setCode(request.code().trim());
        service.setName(request.name().trim());
        service.setDescription(request.description());
        service.setHsnSac(request.hsnSac().trim());
        service.setTaxClass(parseTaxClass(request.taxClass()));
        if (request.defaultGstRate() != null) {
            service.setDefaultGstRate(request.defaultGstRate());
        }
        service.setUnit(request.unit());
        if (request.defaultRate() != null) {
            service.setDefaultRate(request.defaultRate());
        }
        if (request.taxInclusive() != null) {
            service.setTaxInclusive(request.taxInclusive());
        }
        if (request.active() != null) {
            service.setActive(request.active());
        }
    }

    private TaxClass parseTaxClass(String value) {
        try {
            return TaxClass.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Invalid tax class: " + value);
        }
    }

    private ServiceResponse toResponse(CatalogService service) {
        return new ServiceResponse(
            service.getId(), service.getCode(), service.getName(), service.getDescription(),
            service.getHsnSac(), service.getTaxClass().name(), service.getDefaultGstRate(),
            service.getUnit(), service.getDefaultRate(), service.isTaxInclusive(),
            service.isActive(), service.getCreatedAt(), service.getUpdatedAt());
    }
}
package com.leadorbit.billing.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leadorbit.audit.AuditRecord;
import com.leadorbit.audit.AuditService;
import com.leadorbit.audit.domain.AuditOutcome;
import com.leadorbit.billing.domain.SellerProfile;
import com.leadorbit.billing.dto.SellerProfileRequest;
import com.leadorbit.billing.dto.SellerProfileResponse;
import com.leadorbit.billing.repository.SellerProfileRepository;
import com.leadorbit.common.error.ApiException;
import com.leadorbit.security.SecurityUtils;

@Service
public class SellerProfileService {

    private static final String ENTITY = "seller_profile";

    private final SellerProfileRepository repository;
    private final AuditService auditService;

    public SellerProfileService(SellerProfileRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<SellerProfileResponse> list() {
        return repository.findAllByDeletedAtIsNullOrderByLegalNameAsc().stream()
            .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SellerProfileResponse getDefault() {
        return toResponse(requireDefault());
    }

    @Transactional
    public SellerProfileResponse create(SellerProfileRequest request) {
        if (repository.existsByGstin(request.gstin().trim())) {
            throw ApiException.conflict("A seller profile with this GSTIN already exists");
        }
        SellerProfile profile = new SellerProfile();
        apply(profile, request);
        if (profile.isDefault()) {
            clearDefault();
        }
        repository.save(profile);

        auditService.record(AuditRecord.builder()
            .action("billing.seller_profile_create").outcome(AuditOutcome.SUCCESS)
            .actorUserId(SecurityUtils.currentUserId())
            .entityType(ENTITY).entityId(String.valueOf(profile.getId()))
            .summary("Created seller profile " + profile.getGstin())
            .after(toResponse(profile))
            .build());
        return toResponse(profile);
    }

    @Transactional
    public SellerProfileResponse update(Long id, SellerProfileRequest request) {
        SellerProfile profile = requireProfile(id);
        SellerProfileResponse before = toResponse(profile);
        apply(profile, request);
        if (profile.isDefault()) {
            clearDefault();
        }
        repository.save(profile);

        auditService.record(AuditRecord.builder()
            .action("billing.seller_profile_update").outcome(AuditOutcome.SUCCESS)
            .actorUserId(SecurityUtils.currentUserId())
            .entityType(ENTITY).entityId(String.valueOf(id))
            .summary("Updated seller profile " + profile.getGstin())
            .before(before).after(toResponse(profile))
            .build());
        return toResponse(profile);
    }

    private void apply(SellerProfile profile, SellerProfileRequest request) {
        profile.setLegalName(request.legalName().trim());
        profile.setTradeName(request.tradeName());
        profile.setGstin(request.gstin().trim());
        profile.setStateCode(request.stateCode().trim());
        profile.setRegisteredAddress(request.registeredAddress().trim());
        profile.setLogoPath(request.logoPath());
        if (request.isDefault() != null) {
            profile.setDefault(request.isDefault());
        }
        if (request.active() != null) {
            profile.setActive(request.active());
        }
    }

    private void clearDefault() {
        repository.findAllByDeletedAtIsNullOrderByLegalNameAsc().forEach(p -> p.setDefault(false));
    }

    private SellerProfile requireProfile(Long id) {
        return repository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> ApiException.notFound("Seller profile not found"));
    }

    private SellerProfile requireDefault() {
        return repository.findFirstByIsDefaultTrueAndDeletedAtIsNull()
            .orElseThrow(() -> ApiException.notFound("No default seller profile configured"));
    }

    public SellerProfileResponse toResponse(SellerProfile profile) {
        return new SellerProfileResponse(
            profile.getId(), profile.getLegalName(), profile.getTradeName(), profile.getGstin(),
            profile.getStateCode(), profile.getRegisteredAddress(), profile.getLogoPath(),
            profile.isDefault(), profile.isActive(), profile.getCreatedAt());
    }
}
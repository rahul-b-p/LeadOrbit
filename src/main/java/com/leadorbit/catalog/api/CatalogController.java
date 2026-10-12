package com.leadorbit.catalog.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.leadorbit.catalog.dto.GstRateRequest;
import com.leadorbit.catalog.dto.GstRateResponse;
import com.leadorbit.catalog.dto.ServiceRequest;
import com.leadorbit.catalog.dto.ServiceResponse;
import com.leadorbit.catalog.service.GstRateService;
import com.leadorbit.catalog.service.ServiceCatalog;
import com.leadorbit.common.api.ApiResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/catalog")
public class CatalogController {

    private final ServiceCatalog serviceCatalog;
    private final GstRateService gstRateService;

    public CatalogController(ServiceCatalog serviceCatalog, GstRateService gstRateService) {
        this.serviceCatalog = serviceCatalog;
        this.gstRateService = gstRateService;
    }

    @GetMapping("/services")
    @PreAuthorize("hasAuthority('catalog.read')")
    public ApiResponse<List<ServiceResponse>> listServices(
        @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ApiResponse.ok(serviceCatalog.list(includeInactive));
    }

    @GetMapping("/services/{id}")
    @PreAuthorize("hasAuthority('catalog.read')")
    public ApiResponse<ServiceResponse> getService(@PathVariable Long id) {
        return ApiResponse.ok(serviceCatalog.get(id));
    }

    @PostMapping("/services")
    @PreAuthorize("hasAuthority('catalog.manage')")
    public ApiResponse<ServiceResponse> createService(@Valid @RequestBody ServiceRequest request) {
        return ApiResponse.ok("Service created", serviceCatalog.create(request));
    }

    @PatchMapping("/services/{id}")
    @PreAuthorize("hasAuthority('catalog.manage')")
    public ApiResponse<ServiceResponse> updateService(@PathVariable Long id,
                                                      @Valid @RequestBody ServiceRequest request) {
        return ApiResponse.ok("Service updated", serviceCatalog.update(id, request));
    }

    @DeleteMapping("/services/{id}")
    @PreAuthorize("hasAuthority('catalog.manage')")
    public ApiResponse<Void> deactivateService(@PathVariable Long id) {
        serviceCatalog.deactivate(id);
        return ApiResponse.ok("Service deactivated");
    }

    @GetMapping("/gst-rates")
    @PreAuthorize("hasAuthority('catalog.read')")
    public ApiResponse<List<GstRateResponse>> listGstRates(
        @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ApiResponse.ok(gstRateService.list(includeInactive));
    }

    @PostMapping("/gst-rates")
    @PreAuthorize("hasAuthority('catalog.manage')")
    public ApiResponse<GstRateResponse> createGstRate(@Valid @RequestBody GstRateRequest request) {
        return ApiResponse.ok("GST rate created", gstRateService.create(request));
    }

    @PatchMapping("/gst-rates/{id}")
    @PreAuthorize("hasAuthority('catalog.manage')")
    public ApiResponse<GstRateResponse> updateGstRate(@PathVariable Long id,
                                                      @Valid @RequestBody GstRateRequest request) {
        return ApiResponse.ok("GST rate updated", gstRateService.update(id, request));
    }
}
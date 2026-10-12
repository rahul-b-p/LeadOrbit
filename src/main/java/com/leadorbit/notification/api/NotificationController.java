package com.leadorbit.notification.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.leadorbit.common.api.ApiResponse;
import com.leadorbit.notification.dto.DeliveryLogResponse;
import com.leadorbit.notification.dto.NotificationOutboxResponse;
import com.leadorbit.notification.service.NotificationQueryService;
import com.leadorbit.notification.service.OutboxDispatcher;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationQueryService queryService;
    private final OutboxDispatcher dispatcher;

    public NotificationController(NotificationQueryService queryService, OutboxDispatcher dispatcher) {
        this.queryService = queryService;
        this.dispatcher = dispatcher;
    }

    @GetMapping("/outbox")
    @PreAuthorize("hasAuthority('notification.manage')")
    public ApiResponse<List<NotificationOutboxResponse>> listOutbox(
        @RequestParam(required = false) String status) {
        return ApiResponse.ok(queryService.listOutbox(status));
    }

    @GetMapping("/outbox/{id}/deliveries")
    @PreAuthorize("hasAuthority('notification.manage')")
    public ApiResponse<List<DeliveryLogResponse>> listDeliveries(@PathVariable Long id) {
        return ApiResponse.ok(queryService.listDeliveries(id));
    }

    @PostMapping("/dispatch")
    @PreAuthorize("hasAuthority('notification.manage')")
    public ApiResponse<Integer> dispatchNow() {
        return ApiResponse.ok("Dispatch triggered", dispatcher.dispatch());
    }
}
package com.leadorbit.subscription.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leadorbit.subscription.domain.BillingJobItem;
import com.leadorbit.subscription.domain.BillingJobRun;
import com.leadorbit.subscription.dto.BillingJobItemResponse;
import com.leadorbit.subscription.dto.BillingJobRunResponse;
import com.leadorbit.subscription.repository.BillingJobItemRepository;
import com.leadorbit.subscription.repository.BillingJobRunRepository;

@Service
public class BillingJobQueryService {

    private final BillingJobRunRepository runRepository;
    private final BillingJobItemRepository itemRepository;

    public BillingJobQueryService(BillingJobRunRepository runRepository,
                                  BillingJobItemRepository itemRepository) {
        this.runRepository = runRepository;
        this.itemRepository = itemRepository;
    }

    @Transactional(readOnly = true)
    public List<BillingJobRunResponse> listRuns() {
        return runRepository.findAllByOrderByScheduledForDescIdDesc().stream()
            .map(this::toRunResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<BillingJobItemResponse> listItems(Long runId) {
        return itemRepository.findAllByRunIdOrderByIdAsc(runId).stream()
            .map(this::toItemResponse)
            .toList();
    }

    private BillingJobRunResponse toRunResponse(BillingJobRun run) {
        return new BillingJobRunResponse(
            run.getId(), run.getJobName(), run.getScheduledFor(), run.getStatus().name(),
            run.getStartedAt(), run.getFinishedAt(), run.getTotalItems(),
            run.getSuccessCount(), run.getSkippedCount(), run.getFailedCount(), run.getError());
    }

    private BillingJobItemResponse toItemResponse(BillingJobItem item) {
        return new BillingJobItemResponse(
            item.getId(), item.getRun().getId(), item.getSubscription().getId(),
            item.getPeriodStart(), item.getOutcome().name(), item.getMessage(),
            item.getCreatedAt());
    }
}
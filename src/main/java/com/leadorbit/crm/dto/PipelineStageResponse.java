package com.leadorbit.crm.dto;

import java.time.Instant;

public record PipelineStageResponse(
    Long id,
    String code,
    String name,
    Integer sortOrder,
    boolean isWon,
    boolean isLost,
    boolean active,
    Instant createdAt) {
}
package com.leadorbit.common.api;

import java.util.List;

/** Paginated list payload. */
public record PageResult<T>(List<T> data, long total, int page, int limit, int totalPages) {

    public static <T> PageResult<T> of(List<T> data, long total, int page, int limit) {
        int totalPages = limit > 0 ? (int) Math.ceil((double) total / limit) : 0;
        return new PageResult<>(data, total, page, limit, totalPages);
    }
}

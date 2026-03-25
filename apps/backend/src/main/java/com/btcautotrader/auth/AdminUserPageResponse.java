package com.btcautotrader.auth;

import java.util.List;

public record AdminUserPageResponse(
        List<AdminUserItemResponse> items,
        long totalElements,
        int totalPages,
        int page,
        int size,
        boolean hasNext,
        boolean hasPrevious
) {
}

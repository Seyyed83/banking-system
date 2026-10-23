package com.example.banking.common.exception;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path
) {
    public static ApiError of(
            int status,
            String error,
            String message,
            String path) {
        return new ApiError(
                OffsetDateTime.now(ZoneOffset.UTC),
                status,
                error,
                message,
                path
        );
    }
}
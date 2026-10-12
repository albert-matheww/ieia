package com.ieia.common.dto;

import com.ieia.common.exception.ErrorCode;

import java.time.Instant;
import java.util.List;

/**
 * Error body carried inside {@link ApiResponse#error()}.
 * See docs/03-api-specification.md §1.1.
 */
public record ErrorResponse(
        String code,
        String message,
        String path,
        Instant timestamp,
        List<FieldError> fieldErrors
) {
    public record FieldError(String field, String message) {}

    public static ErrorResponse of(ErrorCode code, String message, String path) {
        return new ErrorResponse(code.name(), message, path, Instant.now(), List.of());
    }

    public static ErrorResponse of(ErrorCode code, String message, String path, List<FieldError> fieldErrors) {
        return new ErrorResponse(code.name(), message, path, Instant.now(), fieldErrors);
    }
}

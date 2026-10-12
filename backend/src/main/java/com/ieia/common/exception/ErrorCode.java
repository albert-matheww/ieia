package com.ieia.common.exception;

import org.springframework.http.HttpStatus;

/** Canonical error codes ↔ HTTP status. See docs/03-api-specification.md §17. */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    UNPROCESSABLE(HttpStatus.UNPROCESSABLE_ENTITY),
    ML_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    LLM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) { this.status = status; }

    public HttpStatus status() { return status; }
}

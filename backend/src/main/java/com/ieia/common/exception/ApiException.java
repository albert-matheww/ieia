package com.ieia.common.exception;

/**
 * Base type for all deliberately-thrown domain errors. The
 * {@link com.ieia.common.exception.GlobalExceptionHandler} maps each to the
 * envelope in docs/03-api-specification.md.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ApiException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode getCode() { return code; }

    // ---- Convenience factories -------------------------------------------

    public static ApiException notFound(String what) {
        return new ApiException(ErrorCode.NOT_FOUND, what + " not found");
    }

    public static ApiException conflict(String message) {
        return new ApiException(ErrorCode.CONFLICT, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(ErrorCode.FORBIDDEN, message);
    }

    public static ApiException unprocessable(String message) {
        return new ApiException(ErrorCode.UNPROCESSABLE, message);
    }

    public static ApiException validation(String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, message);
    }

    public static ApiException mlUnavailable(String message, Throwable cause) {
        return new ApiException(ErrorCode.ML_SERVICE_UNAVAILABLE, message, cause);
    }

    public static ApiException llmUnavailable(String message) {
        return new ApiException(ErrorCode.LLM_UNAVAILABLE, message);
    }
}

package com.ieia.common.dto;

/**
 * Uniform success/error envelope for every REST response:
 * <pre>{ "success": true, "data": &lt;payload&gt;, "error": null }</pre>
 * See docs/03-api-specification.md §1.1.
 */
public record ApiResponse<T>(boolean success, T data, ErrorResponse error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static ApiResponse<Void> failure(ErrorResponse error) {
        return new ApiResponse<>(false, null, error);
    }
}

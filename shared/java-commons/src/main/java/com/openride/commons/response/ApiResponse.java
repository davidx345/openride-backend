package com.openride.commons.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Standard API response wrapper for all OpenRide endpoints.
 * Provides consistent response structure across all services.
 *
 * @param <T> The type of data being returned
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private ErrorDetails error;
    private MetaData meta;

    /**
     * Creates a successful response with data.
     *
     * @param data The response data
     * @param <T>  The type of data
     * @return ApiResponse with success=true
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .meta(MetaData.builder()
                        .timestamp(Instant.now())
                        .build())
                .build();
    }

    /**
     * Creates a successful response without data.
     *
     * @param <T> The type of data
     * @return ApiResponse with success=true
     */
    public static <T> ApiResponse<T> success() {
        return ApiResponse.<T>builder()
                .success(true)
                .meta(MetaData.builder()
                        .timestamp(Instant.now())
                        .build())
                .build();
    }

    /**
     * Creates an error response.
     *
     * @param errorCode The error code
     * @param message   The error message
     * @param <T>       The type of data
     * @return ApiResponse with success=false
     */
    public static <T> ApiResponse<T> error(String errorCode, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ErrorDetails.builder()
                        .code(errorCode)
                        .message(message)
                        .build())
                .meta(MetaData.builder()
                        .timestamp(Instant.now())
                        .build())
                .build();
    }

    /**
     * Creates an error response with details.
     *
     * @param errorCode The error code
     * @param message   The error message
     * @param details   Additional error details
     * @param <T>       The type of data
     * @return ApiResponse with success=false
     */
    public static <T> ApiResponse<T> error(String errorCode, String message, Object details) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ErrorDetails.builder()
                        .code(errorCode)
                        .message(message)
                        .details(details)
                        .build())
                .meta(MetaData.builder()
                        .timestamp(Instant.now())
                        .build())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorDetails {
        private String code;
        private String message;
        private Object details;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MetaData {
        private Instant timestamp;
        private String correlationId;
        private Integer page;
        private Integer pageSize;
        private Long totalElements;
    }
}

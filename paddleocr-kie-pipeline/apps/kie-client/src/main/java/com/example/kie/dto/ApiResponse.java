package com.example.kie.dto;

/**
 * [DTO] 표준 API 응답 템플릿
 * 프론트엔드 통신을 위한 공통 규격 정의
 */
public record ApiResponse<T>(
    /** 성공 여부 */
    boolean success,
    /** 결과 메시지 */
    String message,
    /** 응답 데이터 */
    T data
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "Operation successful.", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }
}

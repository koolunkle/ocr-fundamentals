package com.example.kie.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.example.kie.dto.ApiResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * [Handler] 전역 예외 처리기
 * 애플리케이션 내에서 발생하는 예외를 통합 관리하고 공통 응답 규격으로 변환
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * [ERROR] 파일 업로드 용량 초과 처리
     * 설정된 최대 업로드 크기(50MB)를 초과한 요청에 대해 예외 응답
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxSizeException(MaxUploadSizeExceededException exc) {
        log.error("[ERR] 파일 업로드 용량 초과 [메시지: {}]", exc.getMessage());
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.<Void>error("파일 용량이 너무 큽니다. (최대 50MB)"));
    }

    /**
     * [ERROR] 일반 예외 처리
     * 정의되지 않은 모든 서버 내부 오류에 대해 500 에러 응답 및 로그 기록
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception e) {
        log.error("[ERR] 서버 내부 예외 발생 [원인: {}]", e.getMessage(), e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.<Void>error("서버 처리 중 오류가 발생했습니다: " + e.getMessage()));
    }
}

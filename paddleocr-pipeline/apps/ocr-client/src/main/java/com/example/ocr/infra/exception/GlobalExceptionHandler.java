package com.example.ocr.infra.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

/**
 * 프로젝트 전역에서 발생하는 예외를 한곳에서 관리하고 표준화된 오류 응답을 생성합니다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 도메인 비즈니스 예외(OcrException)와 그 하위 예외를 통합 처리합니다.
     */
    @ExceptionHandler(OcrException.class)
    public ResponseEntity<ErrorResponse> handleOcrException(OcrException e) {
        // 비즈니스 수준의 예외는 경고(WARN) 수준으로 로깅합니다.
        log.warn("서비스 예외 감지 - [{}] {}", e.getErrorCode(), e.getMessage());

        var error = ErrorResponse.of(e.getMessage(), e.getErrorCode());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST) // 대부분의 비즈니스 예외는 400 응답
                .body(error);
    }

    /**
     * 잘못된 입력 값이나 비어있는 파일명 등이 전달된 경우 처리합니다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {
        log.warn("유효하지 않은 요청 데이터: {}", e.getMessage());

        var error = ErrorResponse.of(e.getMessage(), "INVALID_INPUT");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * 예상하지 못한 치명적인 시스템 오류를 마지막 단계에서 처리합니다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleInternalError(Exception e) {
        // 시스템의 구조적 문제나 버그일 가능성이 높으므로 ERROR 수준으로 로깅합니다.
        log.error("미처리 전역 예외 발생 - {}: {}", e.getClass().getSimpleName(), e.getMessage());
        log.error("예외 상세 정보(Stack Trace):", e);

        var error = ErrorResponse.of("시스템 서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", "INTERNAL_SERVER_ERROR");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}

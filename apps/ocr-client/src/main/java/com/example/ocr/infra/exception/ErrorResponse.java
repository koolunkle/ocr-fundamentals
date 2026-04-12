package com.example.ocr.infra.exception;

import java.time.LocalDateTime;

/**
 * 사용자에게 오류 정보를 전달하기 위한 공통 응답 규격입니다.
 */
public record ErrorResponse(String message, String code, LocalDateTime timestamp) {
    /** 편리한 객체 생성을 위한 정적 팩토리 메서드입니다. */
    public static ErrorResponse of(String message, String code) {
        return new ErrorResponse(message, code, LocalDateTime.now());
    }
}

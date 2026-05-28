package com.example.ocr.infra.exception;

/**
 * OCR 서비스에서 발생하는 모든 예외의 최상위 클래스입니다.
 */
public class OcrException extends RuntimeException {
    private final String errorCode;

    public OcrException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

/**
 * 외부 API 호출 중 문제가 발생했을 때 던지는 예외입니다.
 */
class ExternalApiException extends OcrException {
    public ExternalApiException(String message) {
        super(message, "EXTERNAL_API_ERROR");
    }
}

/**
 * 문서 파싱 중 논리적인 오류가 발생했을 때 던지는 예외입니다.
 */
class ParsingException extends OcrException {
    public ParsingException(String message) {
        super(message, "PARSING_ERROR");
    }
}

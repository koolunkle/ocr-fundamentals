package com.example.ocr.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 설정 파일(application.yml)의 ocr 접두사를 가진 설정들을 매핑하는 클래스입니다.
 */
@ConfigurationProperties(prefix = "ocr")
public record OcrProperties(Api api) {
    /** 외부 OCR API 연동 정보입니다. */
    public record Api(String url) {
    }
}

package com.example.ocr.infra.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.RequiredArgsConstructor;

/**
 * 애플리케이션 전역에서 사용되는 공통 설정을 관리합니다.
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(OcrProperties.class)
public class AppConfig {
    private final OcrProperties props;

    /** 동기 방식의 외부 통신을 위한 RestClient입니다. */
    @Bean
    RestClient restClient(RestClient.Builder b) {
        return b.baseUrl(props.api().url()).build();
    }

    /** 비동기/스트림 방식의 통신을 위한 WebClient입니다. */
    @Bean
    WebClient webClient(WebClient.Builder b) {
        return b.baseUrl(props.api().url()).build();
    }
}

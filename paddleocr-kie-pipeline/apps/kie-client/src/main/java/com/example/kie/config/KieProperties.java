package com.example.kie.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/**
 * [Config] KIE 서비스 환경 설정
 * 외부 API 주소 및 로컬 모델 경로 정의
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "kie")
public class KieProperties {

    /** [Remote] 외부 연동 API 서버 설정 */
    private final Remote remote = new Remote();

    @Getter @Setter
    public static class Remote {
        /** 외부 추출 서버 URL */
        private String apiUrl;
    }
}

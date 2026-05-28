package com.example.kie.service;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import com.example.kie.config.KieProperties;
import com.example.kie.dto.ExtractionResult;

import lombok.extern.slf4j.Slf4j;

/**
 * [Integration] 원격 KIE API 연동
 * 외부 서버를 활용한 정보 추출 및 결과 데이터 통합
 */
@Slf4j
@Service
public class KieService {

    private final KieProperties kieProperties;
    private final RestClient restClient;

    public KieService(KieProperties kieProperties) {
        this.kieProperties = kieProperties;

        // [Optimization] AI 추론 시간을 고려하여 타임아웃연장
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(600000);
        factory.setConnectTimeout(5000); 

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    /** [Logic] 외부 서버로 추출 작업 위임 */
    public ExtractionResult extract(MultipartFile file) {
        String apiUrl = kieProperties.getRemote().getApiUrl();
        log.info("[API-CALL] 외부 서버 추출 요청 [URL: {}, 파일명: {}]", apiUrl, file.getOriginalFilename());
        
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file.getResource());

        try {
            ExtractionResult result = restClient.post()
                    .uri(apiUrl)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .onStatus(status -> status.isError(), (request, response) -> {
                        log.error("[API-ERR] 외부 서버 응답 오류 [상태코드: {}]", response.getStatusCode());
                        throw new RuntimeException("REMOTE_SERVER_ERROR_" + response.getStatusCode());
                    })
                    .body(ExtractionResult.class);

            log.info("[API-RES] 추출 결과 수신 완료");
            return result;

        } catch (Exception e) {
            log.error("[API-ERR] 통신 레이어 예외 발생 [원인: {}]", e.getMessage());
            throw new RuntimeException("KIE_REMOTE_CONNECTION_FAILED", e);
        }
    }
}

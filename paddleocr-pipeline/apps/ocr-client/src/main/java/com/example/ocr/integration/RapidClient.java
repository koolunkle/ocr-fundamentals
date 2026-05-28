package com.example.ocr.integration;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.example.ocr.infra.exception.OcrException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

/**
 * RapidOCR 엔진 API와 통신하여 이미지 데이터로부터 인식된 텍스트 정보를 가져오는 역할을 수행합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RapidClient implements OcrClient {

    private final RestClient restClient;
    private final WebClient webClient;

    /**
     * 문서를 동기 방식으로 외부 OCR 엔진에 전송하고 전체 결과를 받아옵니다.
     */
    @Override
    public List<RapidModels.Page> process(byte[] fileData, String filename, @Nullable List<Integer> pages) {
        Assert.hasText(filename, "파일명이 누락되었습니다.");

        try {
            var response = restClient.post()
                    .uri("")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(createMultipartRequest(fileData, filename, pages))
                    .retrieve()
                    // 4xx, 5xx 에러가 발생하면 공통 예외를 던집니다.
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new OcrException("분석 서버 응답 에러 (" + res.getStatusCode() + ")", "ENGINE_API_ERROR");
                    })
                    .body(RapidModels.Response.class);

            return (response != null && response.pages() != null) ? response.pages() : List.of();

        } catch (Exception e) {
            log.error("OCR API 호출 실패 [파일명: {}]: {}", filename, e.getMessage());
            // 외부 호출 실패를 내부 도메인 예외로 전환하여 상위로 전달합니다.
            throw new OcrException("이미지 분석 서비스를 현재 이용할 수 없습니다.", "EXTERNAL_SERVICE_UNAVAILABLE");
        }
    }

    /**
     * 외부 OCR 엔진으로부터 실시간으로 페이지별 분석 데이터를 수신하는 스트림을 생성합니다.
     */
    @Override
    public Flux<RapidModels.Page> processStream(byte[] fileData, String filename, @Nullable List<Integer> pages) {
        Assert.hasText(filename, "파일명이 누락되었습니다.");

        return webClient.post()
                .uri("/stream")
                .body(BodyInserters.fromMultipartData(createMultipartRequest(fileData, filename, pages)))
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<RapidModels.Page>>() {})
                // 일시적인 네트워크 불안정 시 자동 재시도 (최대 3회, 지수 백오프 방식)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                        .filter(this::isTransientError)
                        .doBeforeRetry(signal -> log.warn("외부 엔진 연결 재시도 중 (파일명: {}, 횟수: {})", filename, signal.totalRetries() + 1)))
                .mapNotNull(ServerSentEvent::data)
                .onErrorResume(e -> {
                    log.error("OCR 스트림 중단 [파일명: {}]: {}", filename, e.getMessage());
                    // 스트림 중단 시 에러를 전파하여 클라이언트에게 알립니다.
                    return Flux.error(new OcrException("실시간 데이터 분석 처리 중 연결 오류가 발생했습니다.", "STREAMING_FAILURE"));
                });
    }

    /** 재시도가 가능한 유형의 에러(서버 측 오류 또는 타임아웃)인지 판단합니다. */
    private boolean isTransientError(Throwable t) {
        if (t instanceof WebClientResponseException ex) {
            return ex.getStatusCode().is5xxServerError();
        }
        return t instanceof TimeoutException || t instanceof IOException;
    }

    /** 멀티파트 이미지를 포함한 요청 본문을 생성합니다. */
    private MultiValueMap<String, HttpEntity<?>> createMultipartRequest(byte[] bytes, String filename, @Nullable List<Integer> pages) {
        var builder = new MultipartBodyBuilder();

        builder.part("file", new ByteArrayResource(bytes))
                .filename(filename)
                .contentType(MediaType.parseMediaType("image/tiff"));

        // 특정 페이지 분석 요청 시 페이지 번호를 콤마로 구분한 문자열로 추가
        if (pages != null && !pages.isEmpty()) {
            var pageCsv = String.join(",", pages.stream().map(String::valueOf).toList());
            builder.part("pages", pageCsv);
        }

        return builder.build();
    }
}

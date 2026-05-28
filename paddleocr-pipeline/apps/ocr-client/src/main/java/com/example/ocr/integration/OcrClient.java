package com.example.ocr.integration;

import java.util.List;

import org.springframework.lang.Nullable;

import reactor.core.publisher.Flux;

/**
 * 외부 OCR 엔진과 통신하기 위한 공통 인터페이스입니다.
 */
public interface OcrClient {
    /** 이미지 데이터를 전달하여 OCR 결과를 리스트로 받습니다. */
    List<RapidModels.Page> process(byte[] fileData, String filename, @Nullable List<Integer> pages);

    /** 이미지 데이터를 전달하여 OCR 결과를 실시간 스트림으로 받습니다. */
    Flux<RapidModels.Page> processStream(byte[] fileData, String filename, @Nullable List<Integer> pages);
}

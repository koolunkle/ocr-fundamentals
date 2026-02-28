package com.example.ocr.core;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.example.ocr.integration.OcrClient;
import com.example.ocr.integration.RapidModels;
import com.example.ocr.util.AnalysisLogger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * OCR 결과를 받아서 분석하고, 가공하여 최종 결과물을 생성하는 서비스 클래스입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final OcrClient ocrClient;
    private final DocumentParser documentParser;

    /**
     * 문서를 동기 방식으로 분석하여 구조화된 결과를 통합 반환합니다.
     * @param bytes 문서 파일 데이터 (TIFF, JPG 등)
     * @param filename 로그 및 디버깅용 파일명
     * @param pages 분석할 페이지 목록 (null인 경우 전체 분석)
     * @return 최종 분석 결과 컨테이너
     */
    public AnalysisResult.Container analyze(@NonNull byte[] bytes, @NonNull String filename, List<Integer> pages) throws IOException {
        log.info("문서 분석 시작 (파일명: {})", filename);

        // 1. 외부 OCR API 호출
        var ocrPages = ocrClient.process(bytes, filename, pages);
        
        // 2. 각 페이지 데이터를 분석 결과(Page)로 변환
        var analyzedPages = ocrPages.stream()
                .filter(Objects::nonNull)
                .map(page -> dispatch(page, bytes, filename))
                .toList();

        return new AnalysisResult.Container(filename, analyzedPages);
    }

    /**
     * 분석 결과를 페이지별 리액티브 스트림(Flux)으로 전송합니다.
     * 클라이언트가 SSE(Server-Sent Events)를 통해 실시간으로 결과를 받을 수 있게 합니다.
     */
    public Flux<AnalysisResult.Page> stream(@NonNull byte[] bytes, @NonNull String filename, List<Integer> pages) {
        log.info("문서 실시간 분석 시작 (파일명: {})", filename);

        return ocrClient.processStream(bytes, filename, pages)
                .map(page -> dispatch(page, bytes, filename));
    }

    /**
     * 수신된 원본 페이지 데이터를 분석 전략에 따라 적절한 분석 페이지 객체로 변환합니다.
     * Pattern Matching for instanceof를 사용하여 타입을 안전하게 분기합니다.
     */
    private AnalysisResult.Page dispatch(RapidModels.Page raw, byte[] bytes, String filename) {
        try {
            var data = raw.data();

            // 1. 외부 엔진에서 이미 구조화된 정보가 온 경우 (StructuredData)
            if (data instanceof RapidModels.StructuredData s) {
                return handleStructuredPage(raw.pageNum(), s);
            }
            
            // 2. 텍스트 조각들로 구성된 원본 데이터가 온 경우 (RawData)
            if (data instanceof RapidModels.RawData r) {
                return handleRawPage(raw.pageNum(), r, bytes, filename);
            }
            
            // 3. 외부 엔진에서 에러를 명시적으로 반환한 경우
            if (data instanceof RapidModels.GenericData g && "error".equals(raw.type())) {
                log.warn("엔진 경고 [페이지 {}]: {}", raw.pageNum(), g.values());
                return new AnalysisResult.ErrorPage(raw.pageNum(), g.values());
            }

            // 4. 알 수 없는 형식인 경우 기본 결과로 처리
            return new AnalysisResult.RawPage(raw.pageNum(), Map.of("content", List.of()));

        } catch (Exception e) {
            // 한 페이지의 처리가 실패하더라도 시스템 전체가 멈추지 않도록 예외를 페이지 단위로 가둡니다.
            log.error("페이지 처리 중 예상치 못한 오류 발생 (페이지 {}): {}", raw.pageNum(), e.getMessage());
            return new AnalysisResult.ErrorPage(raw.pageNum(), Map.of("message", e.getMessage()));
        }
    }

    /** 이미 구조화된 데이터를 도메인 모델(Document)로 변환하고 로깅합니다. */
    private AnalysisResult.Page handleStructuredPage(int pageNum, RapidModels.StructuredData data) {
        var doc = new AnalysisResult.Document(
                data.court(), 
                data.caseInfo(), 
                data.creditors(), 
                data.debtors(),
                data.thirdDebtors(), 
                data.order(), 
                data.claimAmount(), 
                data.reason()
        );

        AnalysisLogger.logDecision(pageNum, doc);
        
        return new AnalysisResult.StructuredPage(pageNum, doc);
    }

    /** 텍스트 조각들을 합쳐서 직접 문서 구조를 분석하고 시각화 이미지를 생성합니다. */
    private AnalysisResult.Page handleRawPage(int pageNum, RapidModels.RawData raw, byte[] bytes, String filename) {
        var content = raw.content();
        
        if (content == null || content.isEmpty()) {
            return new AnalysisResult.RawPage(pageNum, Map.of("content", List.of()));
        }

        // 텍스트와 좌표 데이터 분리 추출
        var texts = content.stream().map(RapidModels.Item::text).toList();
        var boxes = content.stream().map(RapidModels.Item::box).map(this::toPoints).toList();

        AnalysisLogger.logRaw(pageNum, content);

        // 문서 파서 호출 및 결과 변환
        return Optional.ofNullable(documentParser.parse(texts, boxes))
                .map(res -> {
                    AnalysisLogger.logDecision(pageNum, res.data());
                    
                    return (AnalysisResult.Page) new AnalysisResult.StructuredPage(pageNum, res.data());
                })
                .orElseGet(() -> new AnalysisResult.RawPage(pageNum, Map.of("content", content)));
    }

    /** 박스(Box) 좌표 정보를 [4][2] 정수 배열 포인트 정보로 변환합니다. */
    private int[][] toPoints(RapidModels.Box box) {
        if (box == null) {
            return new int[4][2];
        }
        
        return new int[][] {
                { box.x(), box.y() }, 
                { box.x() + box.w(), box.y() }, 
                { box.x() + box.w(), box.y() + box.h() }, 
                { box.x(), box.y() + box.h() }
        };
    }
}

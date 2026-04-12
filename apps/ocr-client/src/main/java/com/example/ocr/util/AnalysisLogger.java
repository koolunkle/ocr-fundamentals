package com.example.ocr.util;

import java.util.List;
import java.util.stream.Collectors;

import com.example.ocr.core.AnalysisResult;
import com.example.ocr.integration.RapidModels;

import lombok.extern.slf4j.Slf4j;

/**
 * 분석 진행 과정이나 최종 결과를 로그로 기록하여 모니터링을 돕는 로거입니다.
 */
@Slf4j
public class AnalysisLogger {

    /** 
     * 구조화된 문서 정보를 상세하게 로그로 출력합니다. 
     * 분석이 누락된 항목이 있는지 디버깅 시 파악할 수 있습니다.
     */
    public static void logDecision(int page, AnalysisResult.Document doc) {
        log.info("--- [페이지 {}] 문서 분석 리포트 ---", page);
        
        log.info("법원: {}", Optional(doc.court()));
        log.info("사건번호: {}", Optional(doc.caseInfo()));
        
        if (!doc.creditors().isEmpty()) {
            log.info("채권자: {}", doc.creditors());
        }
        
        if (!doc.debtors().isEmpty()) {
            log.info("채무자: {}", doc.debtors());
        }
        
        if (!doc.thirdDebtors().isEmpty()) {
            log.info("제3채무자: {}", doc.thirdDebtors());
        }
        
        if (doc.claimAmount() != null) {
            log.info("청구금액: {}", doc.claimAmount());
        }
        
        if (!doc.order().isEmpty()) {
            log.info("주문: {}", doc.order());
        }
        
        log.info("----------------------------------");
    }

    /** 
     * 추출된 원본 텍스트 조각들의 내용을 로그로 기록합니다. 
     * 인식된 텍스트의 품질을 확인할 때 유용합니다.
     */
    public static void logRaw(int page, List<RapidModels.Item> items) {
        if (items == null || items.isEmpty()) {
            log.debug("--- [페이지 {}] 추출된 텍스트가 없습니다. ---", page);
            return;
        }

        log.debug("--- [페이지 {}] 원본 데이터 분석 (총 {}개 조각) ---", page, items.size());
        
        // 상위 10개의 텍스트 조각만 샘플로 보여줍니다.
        String sample = items.stream()
                .limit(10)
                .map(item -> String.join(" ", item.lines()))
                .collect(Collectors.joining(" | "));
        
        log.debug("텍스트 샘플: {} ...", sample);
    }

    /** 빈 값을 "미인식"으로 표시해주는 헬퍼 메서드입니다. */
    private static String Optional(String value) {
        return (value == null || value.isBlank()) ? "[미인식]" : value;
    }
}

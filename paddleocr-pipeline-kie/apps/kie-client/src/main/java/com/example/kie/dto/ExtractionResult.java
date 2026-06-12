package com.example.kie.dto;

import java.util.Map;

/**
 * [DTO] KIE 분석 결과 데이터
 * 추출된 필드 맵 및 처리 상태 정보
 */
public record ExtractionResult(
    /** 처리 상태 */
    String status,
    
    /** 추출된 정보 (Key-Value Map) */
    Map<String, String> result,
    
    /** 결과 메시지 */
    String message
) {
}

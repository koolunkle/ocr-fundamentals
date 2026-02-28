package com.example.ocr.core;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;

/**
 * 문서 분석 결과의 데이터 구조를 정의합니다.
 * Sealed Interface와 Record를 사용하여 데이터의 불변성과 타입 안전성을 보장합니다.
 */
public interface AnalysisResult {

    /** 최종 분석 결과를 담는 최상위 컨테이너입니다. */
    record Container(String filename, List<Page> pages) {}

    /** 
     * 페이지별 결과를 나타내는 인터페이스입니다. 
     * 정의된 세 가지 클래스 외에는 이 인터페이스를 구현할 수 없습니다.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
    @JsonSubTypes({
        @JsonSubTypes.Type(value = StructuredPage.class, name = "structured"),
        @JsonSubTypes.Type(value = RawPage.class, name = "raw"),
        @JsonSubTypes.Type(value = ErrorPage.class, name = "error")
    })
    sealed interface Page permits StructuredPage, RawPage, ErrorPage {
        @JsonProperty("page_num") int pageNum();
        String type();
    }

    /** 분석이 완료되어 구조화된 데이터가 포함된 페이지 */
    record StructuredPage(@JsonProperty("page_num") int pageNum, Document data) implements Page {
        @Override public String type() { return "structured"; }
    }

    /** 원본 OCR 인식 텍스트만 포함된 페이지 */
    record RawPage(@JsonProperty("page_num") int pageNum, Object data) implements Page {
        @Override public String type() { return "raw"; }
    }

    /** 분석 중 예외가 발생한 페이지 정보를 담는 클래스 */
    record ErrorPage(@JsonProperty("page_num") int pageNum, Object data) implements Page {
        @Override public String type() { return "error"; }
    }

    /**
     * 법원 결정문 등의 문서에서 추출한 핵심 비즈니스 데이터 모델입니다.
     */
    record Document(
        @JsonProperty("법원") String court,
        @JsonProperty("사건") String caseInfo,
        @JsonProperty("채권자") @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<String> creditors,
        @JsonProperty("채무자") @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<String> debtors,
        @JsonProperty("제3채무자") @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<String> thirdDebtors,
        @JsonProperty("주문") @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<String> order,
        @JsonProperty("청구금액") String claimAmount,
        @JsonProperty("이유") String reason
    ) {
        /** 데이터가 비어있는지 확인하는 편의 메서드입니다. */
        @JsonIgnore
        public boolean isEmpty() {
            return court == null && caseInfo == null && creditors.isEmpty() && debtors.isEmpty();
        }
    }

    /** 원본 텍스트 조각과 그 좌표(Bounding Box) */
    record RawItem(String text, int[][] box) {}
}

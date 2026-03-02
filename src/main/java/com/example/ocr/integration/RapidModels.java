package com.example.ocr.integration;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 외부 RapidOCR API의 요청과 응답을 위한 데이터 모델들을 담는 인터페이스입니다.
 */
public interface RapidModels {

    /** 전체 API 응답 구조입니다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(String filename, List<Page> pages) {
    }

    /** 각 페이지별 인식 데이터입니다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Page(
            @JsonProperty("page_num") int pageNum,
            String type,
            @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "type", defaultImpl = GenericData.class) @JsonSubTypes( {
                    @JsonSubTypes.Type(value = RawData.class, name = "raw"),
                    @JsonSubTypes.Type(value = StructuredData.class, name = "structured") }) PageData data){
    }

    /** 페이지 데이터의 다형성을 위한 인터페이스입니다. */
    sealed interface PageData permits RawData, StructuredData, GenericData {
    }

    /** 텍스트 조각들로 구성된 데이터입니다. */
    record RawData(@JsonValue List<Item> content) implements PageData {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public RawData {
        }
    }

    /** 이미 외부에서 구조화가 완료된 문서 정보입니다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record StructuredData(
            @JsonProperty("법원") String court,
            @JsonProperty("사건") String caseInfo,
            @JsonProperty("채권자") @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<String> creditors,
            @JsonProperty("채무자") @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<String> debtors,
            @JsonProperty("제3채무자") @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<String> thirdDebtors,
            @JsonProperty("주문") @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<String> order,
            @JsonProperty("청구금액") String claimAmount,
            @JsonProperty("이유") String reason) implements PageData {
    }

    /** 알 수 없는 타입의 범용 데이터입니다. */
    record GenericData(Map<String, Object> values) implements PageData {
    }

    /** 텍스트 한 조각의 내용과 위치 정보입니다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(
            String type,
            Double score,
            @JsonProperty("rect") Box box,
            List<String> lines) {
    }

    /** 가로줄 병합 전 원본 좌표 정보입니다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Box(int x, int y, int w, int h) {
    }
}

package com.example.ocr.core;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 나열된 OCR 텍스트 데이터를 구조화된 문서 정보로 변환하는 분석기(DocumentParser)를 테스트합니다.
 */
class DocumentParserTest {

    private final FuzzyMatcher fuzzyMatcher = new FuzzyMatcher();
    private final LineMerger lineMerger = new LineMerger();
    private final DocumentParser documentParser = new DocumentParser(fuzzyMatcher, lineMerger);

    @Test
    @DisplayName("각 줄로 나누어진 텍스트 정보를 바탕으로 문서 항목을 정상적으로 파싱한다")
    void parse_Success() {
        // given (분석할 예시 데이터 준비)
        List<String> texts = List.of(
                "인천지방법원", "사건", "2022타채9030", "채권자", "김철수", "채무자", "김영희", "2023. 1. 1."
        );
        
        // 각 필드가 서로 다른 줄(Y값이 50씩 차이남)에 위치하도록 설정
        List<int[][]> boxes = List.of(
                new int[][]{{0, 50}, {100, 50}, {100, 100}, {0, 100}}, // 인천지방법원
                new int[][]{{0, 100}, {100, 100}, {100, 150}, {0, 150}}, // 사건
                new int[][]{{0, 150}, {100, 150}, {100, 200}, {0, 200}}, // 2022타채9030
                new int[][]{{0, 200}, {100, 200}, {100, 250}, {0, 250}}, // 채권자
                new int[][]{{0, 250}, {100, 250}, {100, 300}, {0, 300}}, // 김철수
                new int[][]{{0, 300}, {100, 300}, {100, 350}, {0, 350}}, // 채무자
                new int[][]{{0, 350}, {100, 350}, {100, 400}, {0, 400}}, // 김영희
                new int[][]{{0, 500}, {100, 500}, {100, 550}, {0, 550}}  // 2023. 1. 1.
        );

        // when
        var result = documentParser.parse(texts, boxes);

        // then
        assertThat(result).isNotNull();
        
        var doc = result.data();
        assertThat(doc.court()).isEqualTo("인천지방법원");
        assertThat(doc.caseInfo()).isEqualTo("2022타채9030");
        assertThat(doc.creditors()).containsExactly("김철수");
        assertThat(doc.debtors()).containsExactly("김영희");
    }

    @Test
    @DisplayName("결정문 키워드가 없는 경우 분석 결과는 null이어야 한다")
    void parse_Failure_NoCourtKeyword() {
        // given
        List<String> texts = List.of("일반 텍스트 1", "일반 텍스트 2");
        List<int[][]> boxes = List.of(
                new int[][]{{0, 100}, {100, 100}, {100, 150}, {0, 150}},
                new int[][]{{0, 200}, {100, 200}, {100, 250}, {0, 250}}
        );

        // when
        var result = documentParser.parse(texts, boxes);

        // then
        assertThat(result).isNull();
    }
}

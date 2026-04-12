package com.example.ocr.core;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 텍스트 오타 보정 기능(FuzzyMatcher)의 매칭 정확도를 테스트합니다.
 */
class FuzzyMatcherTest {

    private final FuzzyMatcher fuzzyMatcher = new FuzzyMatcher();

    @Test
    @DisplayName("완벽하게 일치하는 키워드의 위치(인덱스)를 정확히 찾는다")
    void match_Exact() {
        // given
        List<String> texts = List.of("사건", "2023타채1004", "채권자", "김철수");

        // when & then
        assertThat(fuzzyMatcher.match(texts, "사건")).isEqualTo(0);
        assertThat(fuzzyMatcher.match(texts, "채권자")).isEqualTo(2);
    }

    @Test
    @DisplayName("유사도가 임계치(0.7) 이상이면 키워드 위치를 찾아낼 수 있다")
    void match_Fuzzy() {
        // given
        // '채권자' 대신 '채권자들'로 인식된 경우 (유사도: (4-1)/4 = 0.75)
        List<String> texts = List.of("사건", "2023타채1004", "채권자들", "김철수");

        // when & then
        assertThat(fuzzyMatcher.match(texts, "채권자")).isEqualTo(2);
    }

    @Test
    @DisplayName("유사도가 너무 낮은 경우 매칭에 실패(-1)한다")
    void match_NoMatch() {
        // given
        // '사건' 대신 '사거' (유사도: (2-1)/2 = 0.5)
        List<String> texts = List.of("사거", "2023타채1004", "채권쟈", "김철수");

        // when & then
        assertThat(fuzzyMatcher.match(texts, "사건")).isEqualTo(-1);
    }
}

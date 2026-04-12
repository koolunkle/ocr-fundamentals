package com.example.ocr.util;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 텍스트 정제 유틸리티(TextUtil)의 기능을 검증하는 테스트 클래스입니다.
 */
class TextUtilTest {

    @Test
    @DisplayName("허용된 특수 기호는 유지하고 불필요한 공백을 정규화한다")
    void sanitize_NormalCase() {
        // given
        String input = "  인천지방법원!! 2022타채9030  ";

        // when
        String result = TextUtil.sanitize(input);

        // then
        assertThat(result).isEqualTo("인천지방법원!! 2022타채9030");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n", "\t"})
    @DisplayName("비어있거나 공백만 있는 입력은 null을 반환한다")
    void sanitize_EmptyCase(String input) {
        // when & then
        assertThat(TextUtil.sanitize(input)).isNull();
    }

    @Test
    @DisplayName("허용되지 않은 기호(:)는 제거하고 허용된 기호는 유지한다")
    void sanitize_AllowedSpecialCharacters() {
        // given
        // ':' 은 허용된 패턴에 없으므로 제거되어야 함
        String input = "채권자: [김철수] (주)테스트 #!@";

        // when
        String result = TextUtil.sanitize(input);

        // then
        assertThat(result).isEqualTo("채권자 [김철수] (주)테스트 #!@");
    }
}

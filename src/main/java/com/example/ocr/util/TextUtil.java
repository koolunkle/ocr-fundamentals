package com.example.ocr.util;

import java.util.regex.Pattern;
import org.springframework.lang.Nullable;

/**
 * 텍스트 데이터의 불필요한 노이즈를 제거하거나 정제하는 유틸리티 클래스입니다.
 * 이 클래스는 모든 메서드가 정적(static)이므로 인스턴스를 생성할 필요가 없습니다.
 */
public final class TextUtil {

    /** 연속된 공백 문자를 찾기 위한 정규표현식 패턴입니다. (\\s+) */
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    
    /** 
     * 허용된 문자(한글, 영어, 숫자, 기본 기호) 외의 노이즈를 제거하기 위한 정규표현식입니다.
     * [^ ... ] 은 괄호 안의 문자를 제외한 모든 문자를 의미합니다.
     */
    private static final Pattern SANITIZE_PATTERN = Pattern.compile("[^가-힣a-zA-Z0-9()\\-\\[\\]<>!@#$%^&*\\s]");

    /** 외부에서 이 클래스의 객체를 생성하지 못하도록 생성자를 private으로 선언합니다.*/
    private TextUtil() {
        throw new UnsupportedOperationException("이 유틸리티 클래스는 객체를 생성할 수 없습니다.");
    }

    /**
     * 입력받은 문자열에서 특수문자 노이즈를 제거하고 공백을 깔끔하게 정리합니다.
     * 
     * @param input 정제할 원본 문자열
     * @return 정제가 완료된 문자열 (입력이 비어있으면 null 반환)
     */
    @Nullable
    public static String sanitize(@Nullable String input) {
        // 1. 입력값이 비어있거나 공백뿐이라면 즉시 null을 반환합니다. (Fail-Fast)
        if (input == null || input.isBlank()) {
            return null;
        }

        // 2. 허용되지 않은 문자(특수 노이즈)를 모두 제거합니다.
        String sanitized = SANITIZE_PATTERN.matcher(input).replaceAll("");
        
        // 3. 중복된 공백(예: "  ")을 한 칸의 공백(" ")으로 줄이고 양끝 공백을 제거합니다.
        return WHITESPACE_PATTERN.matcher(sanitized).replaceAll(" ").trim();
    }
}

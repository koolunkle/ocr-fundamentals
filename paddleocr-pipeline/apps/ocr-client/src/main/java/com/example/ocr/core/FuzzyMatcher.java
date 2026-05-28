package com.example.ocr.core;

import java.util.List;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.stereotype.Component;

/**
 * 텍스트 매칭 시 오타나 인식 오류를 보정하여 최적의 키워드 위치를 찾는 유틸리티입니다.
 */
@Component
public class FuzzyMatcher {

    private final LevenshteinDistance levenshtein = new LevenshteinDistance();
    private static final double THRESHOLD = 0.7; // 매칭 정확도 임계치

    /**
     * 텍스트 리스트에서 대상 문자열과 가장 유사한 인덱스를 찾습니다.
     */
    public int match(List<String> texts, String target) {
        if (texts == null || target == null) {
            return -1;
        }

        int best = -1;
        double max = 0.0;

        for (int i = 0; i < texts.size(); i++) {
            double ratio = calculate(texts.get(i), target);
            if (ratio > max && ratio >= THRESHOLD) { 
                max = ratio; 
                best = i; 
            }
        }
        return best;
    }

    /** 두 문자열의 유사도를 0~1 사이 값으로 계산합니다. */
    private double calculate(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }

        if (s1.equals(s2)) {
            return 1.0;
        }

        int d = levenshtein.apply(s1, s2);
        int maxLen = Math.max(s1.length(), s2.length());
        
        return 1.0 - (double) d / maxLen;
    }
}

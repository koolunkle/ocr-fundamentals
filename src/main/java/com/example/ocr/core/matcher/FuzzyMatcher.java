package com.example.ocr.core.matcher;

/**
 * 텍스트 오인식 대응을 위한 유사도 분석 유틸리티.
 * Levenshtein Distance 기반.
 */
public final class FuzzyMatcher {

    private FuzzyMatcher() {}

    /** 두 문자열 간의 편집 거리를 계산한다. */
    public static int getDistance(String s1, String s2) {
        if (s1 == null || s2 == null) return Integer.MAX_VALUE;
        if (s1.equals(s2)) return 0;
        
        int n = s1.length();
        int m = s2.length();
        
        if (n == 0) return m;
        if (m == 0) return n;

        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];

        for (int j = 0; j <= m; j++) prev[j] = j;

        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            System.arraycopy(curr, 0, prev, 0, m + 1);
        }
        return prev[m];
    }

    /**
     * 임계값 내에서 패턴이 텍스트에 포함되는지 퍼지 매칭한다.
     */
    public static boolean fuzzyMatch(String text, String pattern, double threshold) {
        if (text == null || pattern == null) return false;
        if (pattern.isEmpty()) return true;
        
        text = text.replaceAll("\\s+", "");
        pattern = pattern.replaceAll("\\s+", "");
        
        if (text.contains(pattern)) return true;
        
        int patternLen = pattern.length();
        int maxDist = (int) (patternLen * (1.0 - threshold));
        
        for (int i = 0; i <= text.length() - patternLen; i++) {
            String sub = text.substring(i, i + patternLen);
            if (getDistance(sub, pattern) <= maxDist) {
                return true;
            }
        }
        
        // 패턴보다 조금 길거나 짧은 경우도 고려
        for (int delta = -1; delta <= 1; delta++) {
            if (delta == 0) continue;
            int windowSize = patternLen + delta;
            if (windowSize <= 0 || windowSize > text.length()) continue;
            
            for (int i = 0; i <= text.length() - windowSize; i++) {
                String sub = text.substring(i, i + windowSize);
                if (getDistance(sub, pattern) <= maxDist) {
                    return true;
                }
            }
        }

        return false;
    }

    /** 유사도를 0.0~1.0 사이의 값으로 반환한다. */
    public static double getSimilarity(String s1, String s2) {
        int dist = getDistance(s1, s2);
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;
        return 1.0 - (double) dist / maxLen;
    }
}

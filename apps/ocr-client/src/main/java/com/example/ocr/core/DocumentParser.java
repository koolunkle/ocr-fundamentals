package com.example.ocr.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.example.ocr.util.TextUtil;

import lombok.RequiredArgsConstructor;

/**
 * 텍스트 조각들로부터 문서의 '사건', '채권자', '주문' 등 주요 섹션의 의미를 파악하는 핵심 파서입니다.
 */
@Component
@RequiredArgsConstructor
public class DocumentParser {

    private final FuzzyMatcher fuzzyMatcher;
    private final LineMerger lineMerger;

    /** 
     * 문서에서 분석해야 할 주요 키워드와 패턴들 
     */
    private static final List<String> ANALYSIS_KEYS = List.of("사건", "채권자", "채무자", "제3채무자", "주문", "청구금액", "이유");
    
    private static final Pattern COURT_PATTERN = Pattern.compile("법[^가-힣0-9]{0,5}원");
    private static final Pattern DECISION_PATTERN = Pattern.compile("결[^가-힣0-9]{0,5}정");
    private static final Pattern DATE_PATTERN = Pattern.compile("20\\d{2}\\.\\s*\\d{1,2}\\.\\s*\\d{1,2}");

    public record Result(AnalysisResult.Document data, List<int[][]> boxes) {}
    private record Section(String key, int startIdx, int endIdx) {}

    /**
     * 나열된 OCR 텍스트들을 병합하고 각 섹션을 추출하여 문서 구조를 완성합니다.
     */
    public Result parse(List<String> rawTexts, List<int[][]> rawBoxes) {
        if (rawTexts == null || rawTexts.isEmpty()) {
            return null;
        }
        
        // 1. 같은 가로줄에 위치한 텍스트들을 병합하여 라인 단위로 재구성합니다.
        var lines = lineMerger.merge(rawTexts, rawBoxes);
        var mergedTexts = lines.stream().map(LineMerger.Line::text).toList();
        var mergedBoxes = lines.stream().map(LineMerger.Line::box).toList();

        // 2. 법원 명칭 및 결정문 본문 시작 위치를 찾습니다.
        int courtIdx = findMatch(mergedTexts, COURT_PATTERN, 5);
        int decisionIdx = findMatch(mergedTexts, DECISION_PATTERN, 10);
        
        if (courtIdx == -1 && decisionIdx == -1) {
            return null; // 결정문이 아닌 것으로 판단
        }

        // 3. 주요 키워드를 바탕으로 문서의 구획(Section)을 나눕니다.
        var sections = mapDocumentSections(mergedTexts);
        
        // 4. 각 구획의 텍스트들을 수집하여 최종 문서 모델을 생성합니다.
        var doc = new AnalysisResult.Document(
            (courtIdx != -1) ? TextUtil.sanitize(mergedTexts.get(courtIdx)) : null,
            getSectionContent(mergedTexts, sections, "사건"),
            getSectionList(mergedTexts, sections, "채권자"),
            getSectionList(mergedTexts, sections, "채무자"),
            getSectionList(mergedTexts, sections, "제3채무자"),
            getSectionList(mergedTexts, sections, "주문"),
            getSectionContent(mergedTexts, sections, "청구금액"),
            getSectionContent(mergedTexts, sections, "이유")
        );
        
        return new Result(doc, mergedBoxes);
    }

    private int findMatch(List<String> texts, Pattern pattern, int searchRange) {
        int range = Math.min(texts.size(), searchRange);
        for (int i = 0; i < range; i++) {
            if (pattern.matcher(texts.get(i)).find()) {
                return i;
            }
        }
        return -1;
    }

    /** 텍스트 리스트에서 분석 대상 키워드들의 시작점과 끝점을 연결하여 섹션 목록을 만듭니다. */
    private List<Section> mapDocumentSections(List<String> texts) {
        List<Section> foundSections = new ArrayList<>();
        
        // 키워드별로 문서 내에서 가장 유사한 텍스트의 위치를 찾습니다.
        for (var key : ANALYSIS_KEYS) {
            int idx = fuzzyMatcher.match(texts, key);
            if (idx != -1) {
                foundSections.add(new Section(key, idx, -1));
            }
        }
        
        // 문서의 흐름 순서(인덱스 순)대로 정렬합니다.
        foundSections.sort((a, b) -> Integer.compare(a.startIdx(), b.startIdx()));
        
        // 각 섹션의 끝점을 다음 섹션의 시작점으로 설정하여 구획을 완성합니다.
        for (int i = 0; i < foundSections.size(); i++) {
            int endIdx = (i < foundSections.size() - 1) 
                    ? foundSections.get(i + 1).startIdx() 
                    : calculateDocumentEnd(texts, foundSections.get(i).startIdx());
            
            foundSections.set(i, new Section(foundSections.get(i).key(), foundSections.get(i).startIdx(), endIdx));
        }
        
        return foundSections;
    }

    /** 문서의 마지막 섹션이 끝나는 지점(날짜가 나오거나 문서 끝까지)을 찾습니다. */
    private int calculateDocumentEnd(List<String> texts, int startIdx) {
        int maxLookAhead = Math.min(texts.size(), startIdx + 100);
        for (int i = startIdx; i < maxLookAhead; i++) {
            if (DATE_PATTERN.matcher(texts.get(i)).find()) {
                return i;
            }
        }
        return texts.size();
    }

    private String getSectionContent(List<String> texts, List<Section> sections, String targetKey) {
        return sections.stream()
                .filter(s -> s.key().equals(targetKey))
                .findFirst()
                .map(s -> String.join(" ", texts.subList(s.startIdx() + 1, s.endIdx())))
                .map(TextUtil::sanitize)
                .orElse(null);
    }

    private List<String> getSectionList(List<String> texts, List<Section> sections, String targetKey) {
        return sections.stream()
                .filter(s -> s.key().equals(targetKey))
                .findFirst()
                .map(s -> texts.subList(s.startIdx() + 1, s.endIdx()).stream()
                        .map(TextUtil::sanitize)
                        .filter(Objects::nonNull)
                        .filter(str -> !str.isBlank())
                        .toList())
                .orElse(List.of());
    }
}

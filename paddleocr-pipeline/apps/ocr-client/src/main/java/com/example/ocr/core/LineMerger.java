package com.example.ocr.core;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 조각난 OCR 텍스트 중에서 같은 가로줄에 위치한 텍스트들을 하나의 줄로 병합하는 도구입니다.
 */
@Component
public class LineMerger {

    private static final int Y_DIFF = 15; // 같은 줄로 판단할 Y축 오차 범위

    public record Line(String text, int[][] box) {}

    /**
     * 위치 좌표(박스) 정보를 바탕으로 텍스트 조각들을 합쳐서 라인 단위로 반환합니다.
     */
    public List<Line> merge(List<String> texts, List<int[][]> boxes) {
        if (texts == null || texts.isEmpty() || boxes == null || boxes.isEmpty()) {
            return List.of();
        }

        List<Line> merged = new ArrayList<>();
        int i = 0;

        while (i < texts.size()) {
            var curText = new StringBuilder(texts.get(i));
            var curBox = boxes.get(i);
            int j = i + 1;

            while (j < texts.size() && isSame(boxes.get(i), boxes.get(j))) {
                curText.append(" ").append(texts.get(j));
                curBox = mergeBox(curBox, boxes.get(j));
                j++;
            }

            merged.add(new Line(curText.toString(), curBox));
            i = j;
        }
        return merged;
    }

    private boolean isSame(int[][] b1, int[][] b2) { 
        return Math.abs(b1[0][1] - b2[0][1]) < Y_DIFF; 
    }

    private int[][] mergeBox(int[][] b1, int[][] b2) {
        int xMin = Math.min(b1[0][0], b2[0][0]);
        int yMin = Math.min(b1[0][1], b2[0][1]);
        int xMax = Math.max(b1[2][0], b2[2][0]);
        int yMax = Math.max(b1[2][1], b2[2][1]);
        
        return new int[][] {
                { xMin, yMin }, 
                { xMax, yMin }, 
                { xMax, yMax }, 
                { xMin, yMax }
        };
    }
}

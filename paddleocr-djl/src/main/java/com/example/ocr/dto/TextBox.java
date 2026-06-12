package com.example.ocr.dto;

import java.util.ArrayList;
import java.util.List;

import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.modality.cv.output.Rectangle;

/**
 * OCR 엔진이 추출한 텍스트 블록의 좌표·텍스트 정보를 캡슐화하는 VO.
 * 좌표계: 이미지 좌상단 (0,0) 기준, 우측(+x), 하단(+y) — 픽셀 단위
 */
public class TextBox {

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final String text;
    private final double confidence;

    public TextBox(int x, int y, int width, int height, String text, double confidence) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.text = text;
        this.confidence = confidence;
    }

    /** DJL DetectedObjects 전체를 TextBox 목록으로 변환 */
    public static List<TextBox> fromDetectedObjects(DetectedObjects detected, int imgWidth, int imgHeight) {
        List<TextBox> boxes = new ArrayList<>();
        for (var item : detected.items()) {
            if (item instanceof DetectedObjects.DetectedObject obj) {
                boxes.add(fromDetectedObject(obj, imgWidth, imgHeight));
            }
        }
        return boxes;
    }

    /** 단일 DetectedObject를 TextBox로 변환 (정규화 좌표 -> 픽셀 좌표) */
    public static TextBox fromDetectedObject(DetectedObjects.DetectedObject obj, int imgWidth, int imgHeight) {
        Rectangle r = obj.getBoundingBox().getBounds();
        int px = (int) (r.getX() * imgWidth);
        int py = (int) (r.getY() * imgHeight);
        int pw = (int) (r.getWidth() * imgWidth);
        int ph = (int) (r.getHeight() * imgHeight);

        return new TextBox(px, py, pw, ph, obj.getClassName(), obj.getProbability());
    }

    public int getCenterX() {
        return x + width / 2;
    }

    public int getCenterY() {
        return y + height / 2;
    }

    /** 우측 끝 x좌표 */
    public int rightEdge() {
        return x + width;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getText() {
        return text;
    }

    public double getConfidence() {
        return confidence;
    }

    @Override
    public String toString() {
        return String.format("OcrBox[(%d,%d) %dx%d '%.20s']", x, y, width, height, text);
    }
}

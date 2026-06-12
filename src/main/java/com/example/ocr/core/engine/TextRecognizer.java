package com.example.ocr.core.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.example.ocr.config.OcrProperties;

import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.output.BoundingBox;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.modality.cv.output.Rectangle;
import ai.djl.modality.cv.util.NDImageUtils;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.opencv.OpenCVImageFactory;
import ai.djl.repository.zoo.ZooModel;

/**
 * PaddleOCR 기반 텍스트 탐지·분류·인식 엔진.
 */
@Component
public class TextRecognizer {

    /** 180도 회전 상태 라벨 */
    private static final String STATE_INVERTED = "Rotate";

    /** 180도 회전 스텝 수 */
    private static final int ROTATION_180_STEPS = 2;

    private final ZooModel<Image, DetectedObjects> detectionModel;
    private final ZooModel<Image, Classifications> classificationModel;
    private final ZooModel<Image, String> recognitionModel;
    private final OcrProperties ocrProperties;

    public TextRecognizer(
            ZooModel<Image, DetectedObjects> detectionModel,
            ZooModel<Image, Classifications> classificationModel,
            ZooModel<Image, String> recognitionModel,
            OcrProperties ocrProperties) {
        this.detectionModel = detectionModel;
        this.classificationModel = classificationModel;
        this.recognitionModel = recognitionModel;
        this.ocrProperties = ocrProperties;
    }

    /** 탐지 모델 Predictor 생성 */
    public Predictor<Image, DetectedObjects> newDetPredictor() {
        return detectionModel.newPredictor();
    }

    /** 분류 모델 Predictor 생성 */
    public Predictor<Image, Classifications> newClsPredictor() {
        return classificationModel.newPredictor();
    }

    /** 인식 모델 Predictor 생성 */
    public Predictor<Image, String> newRecPredictor() {
        return recognitionModel.newPredictor();
    }

    /**
     * 전체 페이지에 대해 탐지→분류→인식 파이프라인을 실행한다.
     */
    public DetectedObjects recognizeFullPage(
            Image image,
            Predictor<Image, DetectedObjects> detPredictor,
            Predictor<Image, Classifications> clsPredictor,
            Predictor<Image, String> recPredictor) throws Exception {

        DetectedObjects detections = detPredictor.predict(image);

        List<String> texts = new ArrayList<>();
        List<Double> probs = new ArrayList<>();
        List<BoundingBox> boxes = new ArrayList<>();

        for (Classifications.Classification item : detections.items()) {
            if (!(item instanceof DetectedObjects.DetectedObject obj)) {
                continue;
            }

            Rectangle r = obj.getBoundingBox().getBounds();
            Image textImg = extractRoi(image, r);
            if (textImg == null) {
                continue;
            }

            // ROI 추출 후 원본 그대로 인식 (DJL REC 모델은 원본 RGB를 기대)
            double ratio = (double) textImg.getWidth() / textImg.getHeight();
            if (ratio < 0.4) {
                continue;
            }

            Classifications clsResult = clsPredictor.predict(textImg);
            if (STATE_INVERTED.equals(clsResult.best().getClassName()) && clsResult.best().getProbability() > 0.98) {
                try (NDManager manager = NDManager.newBaseManager()) {
                    NDArray array = textImg.toNDArray(manager);
                    NDArray rotated = NDImageUtils.rotate90(array, ROTATION_180_STEPS);
                    textImg = OpenCVImageFactory.getInstance().fromNDArray(rotated);
                }
            }

            String rawText = recPredictor.predict(textImg);
            double confidence = obj.getProbability();

            sanitizeText(rawText, confidence).ifPresent(cleanText -> {
                texts.add(cleanText);
                probs.add(confidence);
                boxes.add(obj.getBoundingBox());
            });
        }
        return new DetectedObjects(texts, probs, boxes);
    }

    /** ROI 영역 기준으로 DetectedObjects를 필터링한다. */
    public DetectedObjects filterByRegion(DetectedObjects fullResults, Rectangle roi, int imgW, int imgH) {
        List<String> texts = new ArrayList<>();
        List<Double> probs = new ArrayList<>();
        List<BoundingBox> boxes = new ArrayList<>();

        double roiX1 = roi.getX();
        double roiY1 = roi.getY();
        double roiX2 = roiX1 + roi.getWidth();
        double roiY2 = roiY1 + roi.getHeight();

        for (Classifications.Classification item : fullResults.items()) {
            if (!(item instanceof DetectedObjects.DetectedObject obj)) {
                continue;
            }

            Rectangle box = obj.getBoundingBox().getBounds();
            double cx = box.getX() + box.getWidth() / 2.0;
            double cy = box.getY() + box.getHeight() / 2.0;

            if (cx >= roiX1 && cx <= roiX2 && cy >= roiY1 && cy <= roiY2) {
                texts.add(obj.getClassName());
                probs.add(obj.getProbability());
                boxes.add(obj.getBoundingBox());
            }
        }
        return new DetectedObjects(texts, probs, boxes);
    }

    /** 패딩을 적용하여 ROI 이미지를 추출한다. */
    private Image extractRoi(Image image, Rectangle rect) {
        int padding = ocrProperties.getOptions().getRecPadding();
        int origW = image.getWidth();
        int origH = image.getHeight();

        int x = Math.max(0, (int) (rect.getX() * origW) - padding);
        int y = Math.max(0, (int) (rect.getY() * origH) - padding);
        int w = Math.min(origW - x, (int) (rect.getWidth() * origW) + padding * 2);
        int h = Math.min(origH - y, (int) (rect.getHeight() * origH) + padding * 2);

        return (w <= 0 || h <= 0) ? null : image.getSubImage(x, y, w, h);
    }

    /** 인식 텍스트를 정제하고 유효성을 검증한다. */
    private Optional<String> sanitizeText(String text, double confidence) {
        double minConfidence = ocrProperties.getOptions().getMinConfidence();
        if (text == null || text.isBlank() || confidence < minConfidence) {
            return Optional.empty();
        }

        String clean = text.replace("blank", "").trim();
        if (clean.isEmpty()) {
            return Optional.empty();
        }

        // 허용: 한글, 숫자, 한자, 공백 및 필수 기호 (영문 제거 — 주민등록표에 영문 없음)
        clean = clean.replaceAll("[^가-힣0-9\\p{IsHan}\\s\\.\\,\\-\\(\\)\\/]", "").trim();

        if (clean.length() <= 1 && clean.matches("[\\p{Punct}]")) {
            return Optional.empty();
        }

        return clean.isEmpty() ? Optional.empty() : Optional.of(clean);
    }
}

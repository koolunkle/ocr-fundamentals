package com.example.ocr.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.ocr.core.translator.KoreanRecognitionTranslator;

import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.paddlepaddle.zoo.cv.imageclassification.PpWordRotateTranslator;
import ai.djl.paddlepaddle.zoo.cv.objectdetection.PpWordDetectionTranslator;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;

/**
 * PaddleOCR 모델(DET, CLS, REC) 빈 설정.
 */
@Configuration
public class OcrConfig {

    private static final String PADDLE_ENGINE = "PaddlePaddle";
    private static final String MODEL_NAME = "inference";
    private static final String MODEL_FILE = "inference.pdmodel";

    /** 텍스트 영역 탐지 모델 (DET) */
    @Bean(destroyMethod = "close")
    ZooModel<Image, DetectedObjects> detectionModel(OcrProperties props) throws Exception {
        Path dir = props.getModel().resolveDetPath();
        checkModelFile(dir);

        Map<String, String> options = Map.of(
                "det_db_thresh", String.valueOf(props.getOptions().getDetDbThresh()),
                "det_db_box_thresh", String.valueOf(props.getOptions().getDetDbBoxThresh()),
                "det_db_unclip_ratio", String.valueOf(props.getOptions().getDetDbUnclipRatio()));

        return Criteria.builder()
                .setTypes(Image.class, DetectedObjects.class)
                .optModelUrls(dir.toUri().toString())
                .optModelName(MODEL_NAME)
                .optEngine(PADDLE_ENGINE)
                .optTranslator(new PpWordDetectionTranslator(options))
                .build().loadModel();
    }

    /** 방향 분류 모델 (CLS) */
    @Bean(destroyMethod = "close")
    ZooModel<Image, Classifications> classificationModel(OcrProperties props) throws Exception {
        Path dir = props.getModel().resolveClsPath();
        checkModelFile(dir);

        return Criteria.builder()
                .setTypes(Image.class, Classifications.class)
                .optModelUrls(dir.toUri().toString())
                .optModelName(MODEL_NAME)
                .optEngine(PADDLE_ENGINE)
                .optTranslator(new PpWordRotateTranslator())
                .build().loadModel();
    }

    /** 텍스트 인식 모델 (REC) */
    @Bean(destroyMethod = "close")
    ZooModel<Image, String> recognitionModel(OcrProperties props) throws Exception {
        Path dir = props.getModel().resolveRecPath();
        checkModelFile(dir);

        return Criteria.builder()
                .setTypes(Image.class, String.class)
                .optModelUrls(dir.toUri().toString())
                .optModelName(MODEL_NAME)
                .optEngine(PADDLE_ENGINE)
                .optTranslator(new KoreanRecognitionTranslator())
                .build().loadModel();
    }

    private void checkModelFile(Path dir) {
        Path modelFile = dir.resolve(MODEL_FILE);
        if (!Files.isRegularFile(modelFile)) {
            throw new IllegalStateException("모델 파일이 존재하지 않습니다: " + modelFile.toAbsolutePath());
        }
        if (!Files.isReadable(modelFile)) {
            throw new IllegalStateException("모델 파일 읽기 권한이 없습니다: " + modelFile.toAbsolutePath());
        }
    }
}

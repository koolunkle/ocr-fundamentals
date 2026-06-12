package com.example.ocr.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ocr.dto.OcrRequest;
import com.example.ocr.dto.OcrResponse;
import com.example.ocr.service.OcrService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ocr")
public class OcrController {

    private static final Logger log = LoggerFactory.getLogger(OcrController.class);

    private final OcrService ocrService;

    public OcrController(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    /** 이미지 또는 PDF로부터 텍스트를 추출한다. */
    @PostMapping("/extract")
    public ResponseEntity<OcrResponse> extractText(@ModelAttribute @Valid OcrRequest request) {
        log.info("[작업수신] 요청 ID: {}, 파일 개수: {}", request.getId(),
                (request.getTgtFl() != null ? request.getTgtFl().size() : 0));

        List<OcrResponse.OcrResult> results = ocrService.analyze(request);
        log.info("[작업성공] 요청 ID: {}, 결과 건수: {}", request.getId(), results.size());

        return ResponseEntity.ok(OcrResponse.success(request.getId(), results));
    }
}

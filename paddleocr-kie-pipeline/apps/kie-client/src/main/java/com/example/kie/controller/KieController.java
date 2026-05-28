package com.example.kie.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.example.kie.dto.ApiResponse;
import com.example.kie.dto.ExtractionResult;
import com.example.kie.service.KieService;

import lombok.extern.slf4j.Slf4j;

/**
 * [Endpoint] 문서 정보 추출 제어
 * 외부 분석 요청 수신 및 공통 응답 규격 관리
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/kie")
public class KieController {

    private final KieService kieService;
    
    public KieController(KieService kieService) {
        this.kieService = kieService;
    }

    /**
     * [API] 문서 필드 추출
     * 업로드 파일로부터 주요 정보(Entity)를 추출하여 반환
     */
    @PostMapping("/extract")
    public ResponseEntity<ApiResponse<ExtractionResult>> extract(@RequestParam("file") MultipartFile file) {
        
        log.info("[REQ] KIE 분석 요청 수신 [파일명: {}, 크기: {} bytes]", 
                 file.getOriginalFilename(), file.getSize());

        if (file == null || file.isEmpty()) {
            log.warn("[REQ-ERR] 분석 대상 파일이 누락되었습니다.");
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("파일이 누락되었거나 비어 있습니다."));
        }

        ExtractionResult result = kieService.extract(file);

        log.info("[RES] KIE 분석 처리 완료");
        return ResponseEntity.ok(ApiResponse.success("문서 분석이 완료되었습니다.", result));
    }
}

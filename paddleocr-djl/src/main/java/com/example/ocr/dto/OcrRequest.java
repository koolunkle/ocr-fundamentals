package com.example.ocr.dto;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * OCR 처리 요청 DTO
 */
public class OcrRequest {
    @NotBlank(message = "요청 ID가 누락되었습니다.")
    private final String id;
    
    private final String wrkId;
    private final String callback;
    
    @NotEmpty(message = "처리할 대상 파일이 존재하지 않습니다.")
    private final List<MultipartFile> tgtFl;

    public OcrRequest(String id, String wrkId, String callback, List<MultipartFile> tgtFl) {
        this.id = id;
        this.wrkId = wrkId;
        this.callback = callback;
        this.tgtFl = tgtFl;
    }

    public String getId() {
        return id;
    }

    public String getWrkId() {
        return wrkId;
    }

    public String getCallback() {
        return callback;
    }

    public List<MultipartFile> getTgtFl() {
        return tgtFl;
    }
}

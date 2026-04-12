package com.example.ocr.api;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.ocr.core.AnalysisResult;
import com.example.ocr.core.AnalysisService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

/**
 * 문서 분석 요청을 처리하고 결과를 반환하는 API 컨트롤러입니다.
 */
@RestController
@RequestMapping("/api/v1/ocr")
@RequiredArgsConstructor
@Tag(name = "OCR", description = "문서 인식 및 분석 기능")
@ApiDocs
public class OcrController {

    private final AnalysisService analysisService;

    /**
     * 문서를 분석하여 구조화된 JSON 결과를 한 번에 반환합니다.
     */
    @Operation(summary = "문서 전체 분석")
    @PostMapping(value = "", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnalysisResult.Container analyze(
            @RequestParam("file") MultipartFile file, 
            @RequestParam(value = "pages", required = false) String pages) throws IOException {

        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("unknown");
        
        return analysisService.analyze(file.getBytes(), filename, parsePages(pages));
    }

    /**
     * 문서를 페이지별로 분석하여 실시간 스트림(SSE)으로 전송합니다.
     */
    @Operation(summary = "문서 실시간 스트리밍 분석")
    @PostMapping(value = "/stream", 
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE, 
                 produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AnalysisResult.Page> stream(
            @RequestParam("file") MultipartFile file, 
            @RequestParam(value = "pages", required = false) String pages) throws IOException {

        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("unknown");
        
        return analysisService.stream(file.getBytes(), filename, parsePages(pages));
    }

    /**
     * 콤마로 구분된 페이지 번호 문자열을 리스트로 변환합니다.
     */
    private List<Integer> parsePages(String csv) {
        if (csv == null || csv.isBlank()) {
            return null;
        }

        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .toList();
    }
}

package com.example.ocr;

import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.example.ocr.dto.AnalysisResponse;
import com.example.ocr.service.ProcessorService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Tag("integration")
class OcrPipelineIntegrationTest {

    @Autowired
    private ProcessorService processorService;

    private static final Path SAMPLE_PATH = Paths.get("data", "input", "Ssindoh23010909170.tif");

    @Test
    @DisplayName("실제 이미지를 통한 통합 OCR 파이프라인 검증")
    void analyzeFullPipeline() throws IOException {
        // given
        File sampleFile = SAMPLE_PATH.toFile();
        assumeTrue(sampleFile.exists(), "샘플 이미지 파일이 존재할 때만 테스트를 수행합니다: " + SAMPLE_PATH);

        String docName = "IntegrationTest";
        List<Integer> pages = List.of(1);

        // when
        AnalysisResponse response = processorService.process(sampleFile, pages, docName);

        // then
        assertSoftly(softly -> {
            softly.assertThat(response.fileName()).isEqualTo(docName);
            softly.assertThat(response.results()).isNotEmpty();
            
            AnalysisResponse.PageResult firstPage = response.results().get(0);
            softly.assertThat(firstPage.pageNum()).isEqualTo(1);
            softly.assertThat(firstPage.data()).as("추출된 데이터가 존재해야 함").isNotNull();
        });
    }
}

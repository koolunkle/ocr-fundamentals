package com.example.ocr.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("비즈니스 예외 처리 테스트")
    class BusinessExceptionTest {
        @Test
        @DisplayName("Exception 발생 시 지정된 상태 코드를 반환한다")
        void handleAppException() throws Exception {
            mockMvc.perform(get("/test/app-exception"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value("Not Found Message"));
        }
    }

    @Nested
    @DisplayName("클라이언트 요청 예외 처리 테스트")
    class BadRequestTest {
        @Test
        @DisplayName("파일 용량 초과 시 413 에러를 반환한다")
        void handleMaxSizeExceeded() throws Exception {
            mockMvc.perform(get("/test/size-exceeded"))
                    .andExpect(status().isPayloadTooLarge())
                    .andExpect(jsonPath("$.error").value("Payload Too Large"));
        }

        @Test
        @DisplayName("잘못된 인자 전달 시 400 에러를 반환한다")
        void handleIllegalArgument() throws Exception {
            mockMvc.perform(get("/test/bad-request"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    @RestController
    static class TestController {
        @GetMapping("/test/app-exception")
        public void throwAppException() {
            throw new AppException("Not Found Message", HttpStatus.NOT_FOUND) {};
        }

        @GetMapping("/test/size-exceeded")
        public void throwSizeExceeded() {
            throw new MaxUploadSizeExceededException(1024L);
        }

        @GetMapping("/test/bad-request")
        public void throwBadRequest() {
            throw new IllegalArgumentException("Bad Request");
        }
    }
}

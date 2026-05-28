package com.example.ocr.support;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opencv.core.Mat;

@ExtendWith(MockitoExtension.class)
class MatResourceWrapperTest {

    @Mock
    private Mat mat1;

    @Mock
    private Mat mat2;

    @Test
    @DisplayName("try-with-resources 종료 시 모든 Mat 자원이 해제되어야 한다")
    void shouldReleaseResourcesOnClose() {
        // when
        try (MatResourceWrapper wrapper = new MatResourceWrapper()) {
            wrapper.add(mat1);
            wrapper.add(mat2);
        }

        // then
        verify(mat1).release();
        verify(mat2).release();
    }

    @Test
    @DisplayName("null 객체 등록 시에도 안전하게 동작해야 한다")
    void shouldHandleNullGracefully() {
        // when & then 
        try (MatResourceWrapper wrapper = new MatResourceWrapper()) {
            wrapper.add(null);
        }
    }
}

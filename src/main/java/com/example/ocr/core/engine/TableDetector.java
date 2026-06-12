package com.example.ocr.core.engine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.ocr.config.OcrProperties;

import ai.djl.modality.cv.Image;
import ai.djl.opencv.OpenCVImageFactory;

@Component
public class TableDetector {

    private static final Logger log = LoggerFactory.getLogger(TableDetector.class);
    
    private final OcrProperties ocrProperties;

    public TableDetector(OcrProperties ocrProperties) {
        this.ocrProperties = ocrProperties;
    }

    public static final class TableAnalysisResult {
        private final Image sourceImage;
        private final Image tableImage;
        private final List<Integer> rowBoundaries;
        private final int tableX;
        private final int tableY;

        public TableAnalysisResult(Image sourceImage, Image tableImage, List<Integer> rowBoundaries, int tableX,
                int tableY) {
            this.sourceImage = sourceImage;
            this.tableImage = tableImage;
            this.rowBoundaries = rowBoundaries != null ? List.copyOf(rowBoundaries) : Collections.emptyList();
            this.tableX = tableX;
            this.tableY = tableY;
        }

        public Image getSourceImage() {
            return sourceImage;
        }

        public Image getTableImage() {
            return tableImage;
        }

        public List<Integer> getRowBoundaries() {
            return rowBoundaries;
        }

        public int getTableX() {
            return tableX;
        }

        public int getTableY() {
            return tableY;
        }
    }

    public TableAnalysisResult analyzeTableStructure(Image image, String fileName, int pageNum) {
        Mat src = (Mat) image.getWrappedImage();
        if (src == null) {
            return new TableAnalysisResult(image, image, Collections.emptyList(), 0, 0);
        }

        List<Mat> tracked = new ArrayList<>();
        try {
            var gray = new Mat(); tracked.add(gray);
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);

            var binary = new Mat(); tracked.add(binary);
            Imgproc.adaptiveThreshold(gray, binary, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV,
                    15, 2);
            saveDebugImage(binary, fileName, pageNum, "1_binary");

            // 수평선 탐지
            Mat horizontal = binary.clone(); tracked.add(horizontal);
            int hSize = Math.max(1, horizontal.cols() / 50);
            Mat hStruct = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(hSize, 1)); tracked.add(hStruct);
            Imgproc.erode(horizontal, horizontal, hStruct);
            Imgproc.dilate(horizontal, horizontal, hStruct);

            Mat hDilateStruct = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(1, 2)); tracked.add(hDilateStruct);
            Imgproc.dilate(horizontal, horizontal, hDilateStruct);
            saveDebugImage(horizontal, fileName, pageNum, "2_horizontal");

            // 수직선 탐지
            Mat vertical = binary.clone(); tracked.add(vertical);
            int vSize = Math.max(1, vertical.rows() / 40);
            Mat vStruct = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(1, vSize)); tracked.add(vStruct);
            Imgproc.erode(vertical, vertical, vStruct);
            Imgproc.dilate(vertical, vertical, vStruct);
            saveDebugImage(vertical, fileName, pageNum, "3_vertical");

            // 수평+수직 합성 마스크
            var mask = new Mat(); tracked.add(mask);
            Core.add(horizontal, vertical, mask);
            Mat maskStruct = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3)); tracked.add(maskStruct);
            Imgproc.dilate(mask, mask, maskStruct);
            saveDebugImage(mask, fileName, pageNum, "4_mask");

            // 최대 윤곽 탐색
            List<MatOfPoint> contours = new ArrayList<>();
            var hierarchy = new Mat(); tracked.add(hierarchy);
            Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            Rect maxRect = null;
            double maxArea = 0;
            for (MatOfPoint contour : contours) {
                Rect rect = Imgproc.boundingRect(contour);
                double area = Imgproc.contourArea(contour);
                if (area > maxArea) {
                    maxArea = area;
                    maxRect = rect;
                }
                contour.release();
            }

            if (maxRect == null || maxArea < (src.rows() * src.cols() * 0.01)) {
                return new TableAnalysisResult(image, image, Collections.emptyList(), 0, 0);
            }

            var cropped = new Mat(src, maxRect); tracked.add(cropped);
            saveDebugImage(cropped, fileName, pageNum, "5_crop");

            var horizontalInRoi = new Mat(horizontal, maxRect); tracked.add(horizontalInRoi);
            List<Integer> rowBoundaries = processRowBoundaries(extractRawRowBoundaries(horizontalInRoi));

            return new TableAnalysisResult(
                    image,
                    OpenCVImageFactory.getInstance().fromImage(cropped.clone()),
                    rowBoundaries,
                    maxRect.x,
                    maxRect.y);
        } finally {
            for (Mat mat : tracked) {
                if (mat != null) mat.release();
            }
        }
    }

    private List<Integer> extractRawRowBoundaries(Mat horizontal) {
        List<Integer> boundaries = new ArrayList<>();
        Mat rowSum = new Mat();
        try {
            Core.reduce(horizontal, rowSum, 1, Core.REDUCE_SUM, CvType.CV_32F);
            // 기울어진 선 대응을 위해 낮췄던 임계값을 다시 어느 정도 확보하여 노이즈 제거 (0.05 -> 0.08)
            float threshold = (float) (horizontal.cols() * 255 * 0.08);

            boolean inLine = false;
            int lineStart = 0;

            for (int y = 0; y < rowSum.rows(); y++) {
                float val = (float) rowSum.get(y, 0)[0];
                boolean hasLine = val > threshold;

                if (hasLine && !inLine) {
                    inLine = true;
                    lineStart = y;
                } else if (!hasLine && inLine) {
                    inLine = false;
                    boundaries.add((lineStart + y) / 2);
                }
            }
            if (inLine) {
                boundaries.add((lineStart + horizontal.rows()) / 2);
            }

            if (boundaries.isEmpty()) {
                boundaries.add(0);
                boundaries.add(horizontal.rows());
            } else {
                if (boundaries.get(0) > 5) {
                    boundaries.add(0, 0);
                }
                if (boundaries.get(boundaries.size() - 1) < horizontal.rows() - 5) {
                    boundaries.add(horizontal.rows());
                }
            }
        } finally {
            rowSum.release();
        }
        return boundaries;
    }

    private List<Integer> processRowBoundaries(List<Integer> rawBoundaries) {
        if (rawBoundaries.isEmpty())
            return rawBoundaries;

        List<Integer> refined = new ArrayList<>();
        int lastY = rawBoundaries.get(0);
        refined.add(lastY);

        for (int i = 1; i < rawBoundaries.size(); i++) {
            int currentY = rawBoundaries.get(i);
            // 행 높이 최소값을 다시 조정 (20 -> 30)
            if (currentY - lastY > 30) {
                refined.add(currentY);
                lastY = currentY;
            }
        }

        return refined;
    }

    private void saveDebugImage(Mat mat, String fileName, int pageNum, String suffix) {
        if (!ocrProperties.getRendering().isDebug())
            return;

        try {
            String cleanName = fileName.replaceAll("[^a-zA-Z0-9.-]", "_");
            Path targetPath = Paths.get(ocrProperties.getPaths().getDebugDir(), cleanName, "p" + pageNum);
            Files.createDirectories(targetPath);
            Imgcodecs.imwrite(targetPath.resolve(suffix + ".png").toString(), mat);
        } catch (Exception e) {
            log.warn("디버그 이미지 저장 실패: {}", e.getMessage());
        }
    }

    public void saveDebugRegion(Image image, String fileName, int pageNum, String suffix) {
        if (ocrProperties.getRendering().isDebug() && image.getWrappedImage() instanceof Mat mat) {
            saveDebugImage(mat, fileName, pageNum, suffix);
        }
    }
}

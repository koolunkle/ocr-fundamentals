package com.example.ocr.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.ocr.config.OcrProperties;
import com.example.ocr.core.engine.TableDetector;
import com.example.ocr.core.engine.TableParser;
import com.example.ocr.core.engine.TextRecognizer;
import com.example.ocr.core.engine.TableDetector.TableAnalysisResult;
import com.example.ocr.dto.OcrRequest;
import com.example.ocr.dto.OcrResponse.AddressInfo;
import com.example.ocr.dto.OcrResponse.OcrResult;
import com.example.ocr.dto.TextBox;
import com.example.ocr.core.matcher.FuzzyMatcher;

import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.modality.cv.output.Rectangle;
import ai.djl.opencv.OpenCVImageFactory;

/**
 * OCR 분석 서비스. 
 * PDF 문서를 페이지별로 처리하여 구조화된 결과를 반환한다.
 */
@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    private static final double FUZZY_THRESHOLD = 0.8;

    private static final Pattern ISSU_ID_PATTERN = Pattern.compile("(\\d{4})[\\-\\s]?(\\d{4})[\\-\\s]?(\\d{4})[\\-\\s]?(\\d{4})");
    private static final Pattern ISSU_DATE_PATTERN = Pattern.compile("\\d{4}년\\s?\\d{1,2}월\\s?\\d{1,2}일");

    private final OcrProperties ocrProperties;
    private final TextRecognizer textRecognizer;
    private final TableDetector tableDetector;
    private final TableParser addressTableParser;

    public OcrService(
            OcrProperties ocrProperties,
            TextRecognizer textRecognizer,
            TableDetector tableDetector,
            TableParser addressTableParser) {
        this.ocrProperties = ocrProperties;
        this.textRecognizer = textRecognizer;
        this.tableDetector = tableDetector;
        this.addressTableParser = addressTableParser;
    }

    /** 요청에 포함된 파일들을 순차적으로 분석한다. */
    public List<OcrResult> analyze(OcrRequest request) {
        String requestId = request.getId();
        log.info("[작업시작] 요청 ID: {}, 대상 파일 수: {}", requestId, request.getTgtFl().size());

        return request.getTgtFl().stream()
                .map(file -> processDocument(file, requestId))
                .filter(Objects::nonNull)
                .toList();
    }

    /** 단일 PDF 문서를 페이지별로 순차 처리한다. */
    private OcrResult processDocument(MultipartFile file, String requestId) {
        String fileName = file.getOriginalFilename();
        var resultBuilder = OcrResult.builder()
                .filePath(fileName != null ? fileName : requestId)
                .cpyOfRsdrRegstYn(false);

        try {
            byte[] fileBytes = file.getBytes();
            try (var rar = new RandomAccessReadBuffer(fileBytes);
                    PDDocument document = Loader.loadPDF(rar)) {

                var pdfRenderer = new PDFRenderer(document);
                int totalPages = document.getNumberOfPages();

                List<AddressInfo> totalAddresses = new ArrayList<>();

                for (int i = 0; i < totalPages; i++) {
                    try {
                        BufferedImage rendered = pdfRenderer.renderImageWithDPI(i, ocrProperties.getRendering().getDpi());
                        PageResult pr = processPage(rendered, i, totalPages, fileName);
                        
                        if (pr.cpyOfRsdrRegstYn) resultBuilder.cpyOfRsdrRegstYn(true);
                        if (pr.issuIdntyNo != null) resultBuilder.issuIdntyNo(pr.issuIdntyNo);
                        if (pr.issuDt != null) resultBuilder.issuDt(pr.issuDt);
                        if (pr.issuCharg != null) resultBuilder.issuCharg(pr.issuCharg);
                        if (pr.name != null) resultBuilder.name(pr.name);
                        if (pr.chnchrName != null) resultBuilder.chnchrName(pr.chnchrName);
                        if (pr.rrn != null) resultBuilder.rrn(pr.rrn);
                        totalAddresses.addAll(pr.addresses);
                    } catch (Exception e) {
                        log.error("[페이지 분석 실패] {} - {}/{}: {}", fileName, i + 1, totalPages, e.getMessage());
                    }
                }

                return resultBuilder.addrList(fillMissingNumbers(totalAddresses)).build();
            }
        } catch (Exception e) {
            log.error("[분석 실패] {}: {}", fileName, e.getMessage(), e);
            return resultBuilder.addrList(List.of()).build();
        }
    }

    /** 페이지별 병렬 처리 결과를 담는 내부 클래스 */
    private static class PageResult {
        boolean cpyOfRsdrRegstYn;
        String issuIdntyNo;
        String issuDt;
        String issuCharg;
        String name;
        String chnchrName;
        String rrn;
        List<AddressInfo> addresses = new ArrayList<>();
    }

    /** 단일 페이지를 독립적으로 처리한다. Predictor를 스레드별로 생성하여 thread-safety를 보장한다. */
    private PageResult processPage(BufferedImage rendered, int pageIdx, int totalPages, String fileName) throws Exception {
        log.info("[작업중] {} - {}/{}", fileName, pageIdx + 1, totalPages);

        PageResult pr = new PageResult();
        Image pageImage = convertToImage(rendered);
        rendered.flush();
        int pageW = pageImage.getWidth();
        int pageH = pageImage.getHeight();

        Image preprocessed = preprocessPage(pageImage);

        try (Predictor<Image, DetectedObjects> det = textRecognizer.newDetPredictor();
             Predictor<Image, Classifications> cls = textRecognizer.newClsPredictor();
             Predictor<Image, String> rec = textRecognizer.newRecPredictor()) {

            DetectedObjects allTexts = textRecognizer.recognizeFullPage(preprocessed, det, cls, rec);
            TableAnalysisResult analysis = tableDetector.analyzeTableStructure(pageImage, fileName, pageIdx + 1);

            List<Integer> rows = new ArrayList<>(analysis.getRowBoundaries());
            int tableY = analysis.getTableY();

            // 헤더 추출 (첫 페이지만)
            if (pageIdx == 0) {
                int headerHeight = (tableY > 0) ? tableY : (int) (pageH * 0.15);
                Rectangle headerRoi = new Rectangle(0, 0, 1.0, (double) headerHeight / pageH);
                DetectedObjects headerText = textRecognizer.filterByRegion(allTexts, headerRoi, pageW, pageH);
                extractIssuanceInfoToPageResult(headerText, pr);
                tableDetector.saveDebugRegion(
                        pageImage.getSubImage(0, 0, pageW, headerHeight), fileName, pageIdx + 1, "crop_1_header");
            }

            // 인적사항 추출 (첫 페이지만)
            if (pageIdx == 0) {
                processPersonalInfoToPageResult(allTexts, pageImage, fileName, pageIdx + 1, rows, tableY, pr);
            }

            // 테이블 데이터 추출 (모든 페이지)
            List<TextBox> allBoxes = TextBox.fromDetectedObjects(allTexts, pageW, pageH);

            if (rows.size() >= 2) {
                int cropTop = Math.max(0, tableY + rows.get(1));
                int cropBottom = Math.min(pageH, tableY + rows.get(rows.size() - 1));

                if (cropBottom > cropTop) {
                    List<TextBox> tableBoxes = filterByY(allBoxes, cropTop, cropBottom);

                    int safeH = Math.min(cropBottom - cropTop, pageH - cropTop);
                    if (safeH > 0) {
                        tableDetector.saveDebugRegion(
                                pageImage.getSubImage(0, cropTop, pageW, safeH),
                                fileName, pageIdx + 1, "crop_3_table");
                    }
                    
                    pr.addresses.addAll(addressTableParser.parse(tableBoxes));
                }
            } else {
                log.warn("[Fallback] OpenCV 행 탐지 실패. 전체 페이지에서 추출을 시도합니다.");
                pr.addresses.addAll(addressTableParser.parseFull(new ArrayList<>(allBoxes)));
            }
        }

        return pr;
    }

    /** 발급 정보를 PageResult에 추출한다. */
    private void extractIssuanceInfoToPageResult(DetectedObjects text, PageResult pr) {
        for (Classifications.Classification item : text.items()) {
            if (!(item instanceof DetectedObjects.DetectedObject obj)) continue;
            String rawText = obj.getClassName();
            String cleanText = rawText.replace(" ", "");

            if (FuzzyMatcher.fuzzyMatch(cleanText, "주민등록표", FUZZY_THRESHOLD) ||
                    FuzzyMatcher.fuzzyMatch(cleanText, "초본", FUZZY_THRESHOLD)) {
                pr.cpyOfRsdrRegstYn = true;
            }
            Matcher idMatcher = ISSU_ID_PATTERN.matcher(cleanText);
            if (idMatcher.find()) pr.issuIdntyNo = idMatcher.group(1) + idMatcher.group(2) + idMatcher.group(3) + idMatcher.group(4);

            Matcher dateMatcher = ISSU_DATE_PATTERN.matcher(rawText);
            if (dateMatcher.find()) {
                String dateStr = dateMatcher.group().replaceAll("[^0-9]", "");
                if (dateStr.length() >= 8) pr.issuDt = dateStr.substring(0, 8);
            }
            if (cleanText.endsWith("시장") || cleanText.endsWith("청장") || cleanText.endsWith("동장")) {
                pr.issuCharg = rawText.replaceAll("\\s+", "");
            }
        }
    }

    /** 인적사항을 PageResult에 추출한다. */
    private void processPersonalInfoToPageResult(DetectedObjects allTexts, Image pageImage, String fileName,
            int pageNum, List<Integer> rows, int tableY, PageResult pr) {
        int pageW = pageImage.getWidth();
        int pageH = pageImage.getHeight();

        if (rows.size() >= 2) {
            int startY = Math.max(0, tableY + rows.get(0) - 5);
            int endY = Math.min(pageH, tableY + rows.get(1) + 5);
            int safeH = endY - startY;
            if (safeH > 0) {
                var infoRoi = new Rectangle(0, (double) startY / pageH, 1.0, (double) safeH / pageH);
                DetectedObjects infoText = textRecognizer.filterByRegion(allTexts, infoRoi, pageW, pageH);
                extractPersonalInfoToPageResult(infoText, pr);
                tableDetector.saveDebugRegion(
                        pageImage.getSubImage(0, startY, pageW, safeH), fileName, pageNum, "crop_2_info");
            }
        }
    }

    /** 인적사항(성명, 한자, 주민등록번호)을 PageResult에 추출한다. */
    private void extractPersonalInfoToPageResult(DetectedObjects text, PageResult pr) {
        List<DetectedObjects.DetectedObject> objs = new ArrayList<>();
        for (Classifications.Classification item : text.items()) {
            if (item instanceof DetectedObjects.DetectedObject obj) objs.add(obj);
        }
        if (objs.isEmpty()) return;

        objs.sort(Comparator.comparingDouble(a -> a.getBoundingBox().getBounds().getX()));
        var areaBuilder = new StringBuilder();
        for (DetectedObjects.DetectedObject obj : objs) areaBuilder.append(obj.getClassName()).append(" ");
        String combinedText = areaBuilder.toString();

        String digitsOnly = combinedText.replaceAll("[^0-9]", "");
        if (digitsOnly.length() >= 13) {
            Matcher m = Pattern.compile("\\d{13}").matcher(digitsOnly);
            if (m.find()) pr.rrn = m.group();
            else if (digitsOnly.length() == 13) pr.rrn = digitsOnly;
        }

        String cleanData = combinedText.replaceAll("[^가-힣\\p{IsHan}\\s]", " ").trim();
        String[] keywords = { "성명", "성 명", "주민등록번호", "주 민 등 록 번 호", "한자", "주민", "등록", "번호" };
        String processedData = cleanData;
        for (String kw : keywords) processedData = processedData.replace(kw, " ");
        processedData = processedData.replaceAll("\\s+", " ").trim();

        Matcher chnchrMatcher = Pattern.compile("[\\p{IsHan}]+").matcher(processedData);
        if (chnchrMatcher.find()) {
            pr.chnchrName = chnchrMatcher.group();
            processedData = processedData.replace(chnchrMatcher.group(), "").trim();
        }

        for (String part : processedData.split("\\s+")) {
            String nameCandidate = part.trim();
            if (nameCandidate.startsWith("명") && nameCandidate.length() > 1) nameCandidate = nameCandidate.substring(1);
            if (nameCandidate.startsWith("자") && nameCandidate.length() > 1) nameCandidate = nameCandidate.substring(1);
            if (nameCandidate.length() >= 2 && nameCandidate.length() <= 5 && nameCandidate.matches("^[가-힣]+$")) {
                pr.name = nameCandidate;
                break;
            }
        }
    }

    /** Y좌표 범위로 박스를 필터링한다. */
    private List<TextBox> filterByY(List<TextBox> boxes, int yMin, int yMax) {
        return boxes.stream()
                .filter(b -> b.getCenterY() >= yMin && b.getCenterY() <= yMax)
                .collect(Collectors.toList());
    }

    /**
     * 번호가 누락된 레코드를 이전 번호 기준으로 보정한 뒤,
     * 같은 번호의 연속된 레코드를 하나로 병합한다.
     *
     * 주민등록표 초본의 한 레코드는 물리적으로 상/하 2줄로 구성되어 있어,
     * OCR이 이를 별개 레코드로 분할하는 경우가 있다.
     * 예: no=1 [주소, 날짜, 세대주관계] + no=1 [주소 연장, 변동사유, 등록상태]
     */
    private List<AddressInfo> fillMissingNumbers(List<AddressInfo> items) {
        if (items.isEmpty()) return items;

        // 1단계: 번호 보정
        List<AddressInfo> numbered = new ArrayList<>();
        int lastNo = 0;

        for (AddressInfo item : items) {
            int currentNo;
            try {
                currentNo = Integer.parseInt(item.getNo());
                lastNo = currentNo;
            } catch (NumberFormatException e) {
                currentNo = lastNo + 1;
                lastNo = currentNo;
            }

            numbered.add(new AddressInfo(
                    String.valueOf(currentNo),
                    item.getAddr(), item.getOccDt(), item.getDclrDt(),
                    item.getHhrRel(), item.getChgRsn(), item.getRegSt()));
        }

        // 2단계: 같은 번호의 연속된 레코드 병합
        List<AddressInfo> merged = new ArrayList<>();

        for (AddressInfo item : numbered) {
            if (!merged.isEmpty()) {
                int lastIdx = merged.size() - 1;
                if (merged.get(lastIdx).getNo().equals(item.getNo())) {
                    AddressInfo prev = merged.remove(lastIdx);
                    merged.add(mergeRecords(prev, item));
                } else {
                    merged.add(item);
                }
            } else {
                merged.add(item);
            }
        }

        return merged;
    }

    /**
     * 두 레코드를 하나로 병합한다.
     * 각 필드에 대해 비어있지 않은 값을 우선 채택하고, 주소는 이어붙인다.
     */
    private AddressInfo mergeRecords(AddressInfo a, AddressInfo b) {
        return new AddressInfo(
                a.getNo(),
                joinNonEmpty(" ", a.getAddr(), b.getAddr()),
                firstNonEmpty(a.getOccDt(), b.getOccDt()),
                firstNonEmpty(a.getDclrDt(), b.getDclrDt()),
                firstNonEmpty(a.getHhrRel(), b.getHhrRel()),
                firstNonEmpty(a.getChgRsn(), b.getChgRsn()),
                firstNonEmpty(a.getRegSt(), b.getRegSt()));
    }

    /** 비어있지 않은 첫 번째 값을 반환한다. */
    private String firstNonEmpty(String a, String b) {
        return (a != null && !a.isBlank()) ? a : (b != null ? b : "");
    }

    /** 비어있지 않은 값들을 구분자로 이어붙인다. */
    private String joinNonEmpty(String delimiter, String... parts) {
        var sb = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                if (!sb.isEmpty()) sb.append(delimiter);
                sb.append(part);
            }
        }
        return sb.toString();
    }

    /** BufferedImage를 DJL Image로 변환한다. (BMP 무압축으로 변환 속도 최적화) */
    private Image convertToImage(BufferedImage bufferedImage) throws IOException {
        try (var baos = new ByteArrayOutputStream()) {
            ImageIO.write(bufferedImage, "bmp", baos);
            return OpenCVImageFactory.getInstance().fromInputStream(new ByteArrayInputStream(baos.toByteArray()));
        }
    }

    /**
     * 전체 페이지 이미지에 전처리를 적용한다.
     * 그레이스케일 → 가우시안 블러 → Otsu 이진화 → 침식으로 워터마크/노이즈를 제거한다.
     */
    private Image preprocessPage(Image pageImage) {
        if (!(pageImage.getWrappedImage() instanceof Mat src)) {
            return pageImage;
        }

        Mat gray = new Mat();
        Mat blurred = new Mat();
        Mat binary = new Mat();
        Mat result = new Mat();
        try {
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.GaussianBlur(gray, blurred, new Size(3, 3), 0);
            Imgproc.threshold(blurred, binary, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
            // var kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2, 2));
            // Imgproc.erode(binary, result, kernel);
            // kernel.release();
            binary.copyTo(result);
            return OpenCVImageFactory.getInstance().fromImage(result.clone());
        } finally {
            gray.release();
            blurred.release();
            binary.release();
            result.release();
        }
    }
}

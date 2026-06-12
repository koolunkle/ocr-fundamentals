package com.example.ocr.core.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.ocr.core.matcher.FuzzyMatcher;
import com.example.ocr.dto.OcrResponse.AddressInfo;
import com.example.ocr.dto.TextBox;

/**
 * 주민등록표(초본) 주소 변동 내역의 좌표 기반 구조화 파서.
 *
 * 문서 물리 구조 (4컬럼, 상/하 분리):
 * ┌──────┬──────────────────┬──────────────────┬──────────────────┐
 * │    번호                 주소 (위/아래)                         발생일/신고일                          세대주및관계 
 * │                                                                  변동사유                               등록상태 
 * └──────┴──────────────────┴──────────────────┴──────────────────┘
 *
 * 파싱 전략:
 * - parse(): OpenCV가 테이블 영역을 crop한 박스 → ROI 탐지 불필요, 전체가 데이터
 * - parseFull(): 전체 페이지 박스 (Fallback) → 종결 키워드로 유효 범위 특정
 */
@Component
public class TableParser {

    private static final int Y_TOLERANCE = 25;
    private static final int NO_LIMIT = 100;
    private static final double FUZZY_TH = 0.8;

    private static final Pattern P_NO = Pattern.compile("^\\d{1,3}$");
    // 발생일/신고일: 3~4자리 연도 + 구분자 + 1~2자리 월 + 구분자 + 1~2자리 일 (OCR 오인식 "011-10-31" 대응)
    // 날짜: 4자리 연도 + 구분자(필수) + 1~2자리 월 + 구분자(필수) + 1~2자리 일
    private static final Pattern P_DATE = Pattern.compile("(\\d{2,4})[^0-9]+(\\d{1,2})[^0-9]+(\\d{1,2})");
    private static final Pattern P_CLEAN = Pattern.compile("[^가-힣0-9\\p{IsHan}\\s\\.\\,\\-\\(\\)\\/]");

    private static final List<String> KW_END = List.of("정부24", "이하여백");
    private static final List<String> KW_START = List.of("번호", "주소", "발생일", "신고일", "세대주", "관계");
    private static final List<String> KW_REG_STATUS = List.of("거주자");
    
    // 헤더 하단 파편 판별용
    private static final List<String> KW_HEADER_PARTS = List.of("변동", "사유", "등록", "상태", "발생일", "신고일", "세대주", "관계");
    private static final List<String> KW_DATA_SAFE = List.of("전입", "세대합가", "세대주변경");
    private static final String TERM_SET = "이하여백=";

    // ═══════════════════════════════════════════════════════════════
    // 공개 API
    // ═══════════════════════════════════════════════════════════════

    /**
     * 테이블 영역의 박스를 파싱합니다.
     * 이 영역에는 테이블 헤더 행이 포함되어 있으므로:
     * 1) 헤더 행을 찾아 앵커링
     * 2) 종결 키워드로 유효 Y범위 특정
     * 3) 헤더 다음 행부터 종결 전까지 데이터 추출
     */
    public List<AddressInfo> parse(List<TextBox> boxes) {
        if (boxes == null || boxes.isEmpty())
            return List.of();

        int pw = estimatePageWidth(boxes);
        List<List<TextBox>> allLines = clusterByY(boxes);

        int headerIdx = findStartLine(allLines);
        int endIdx = findEndLine(allLines);
        int dataStart = (headerIdx >= 0) ? headerIdx + 1 : 0;

        // 헤더 2번째 줄 스킵: "변동사유", "등록상태" 등 헤더 파편으로만 구성된 행
        while (dataStart < endIdx && isHeaderFragment(allLines.get(dataStart))) {
            dataStart++;
        }

        if (dataStart >= endIdx) {
            return List.of();
        }

        List<List<TextBox>> dataLines = allLines.subList(dataStart, endIdx);
        double[] anchors;

        if (headerIdx >= 0) {
            anchors = detectAnchorsFromHeader(allLines.get(headerIdx), pw);
            refineAnchorsFromData(dataLines, anchors, pw);
        } else {
            anchors = new double[] { 0.0, 0.05, 0.55, 0.85 };
            refineAnchorsFromData(dataLines, anchors, pw);
        }

        return buildRecords(dataLines, anchors, pw);
    }

    /**
     * Fallback: 전체 페이지 박스 — 테이블 시작/끝을 키워드로 특정.
     */
    public List<AddressInfo> parseFull(List<TextBox> boxes) {
        if (boxes == null || boxes.isEmpty())
            return List.of();

        int pw = estimatePageWidth(boxes);
        List<List<TextBox>> allLines = clusterByY(boxes);

        int startIdx = findStartLine(allLines);
        int endIdx = findEndLine(allLines);
        
        if (startIdx == -1) {
            return List.of();
        }
        
        int dataStart = startIdx + 1;
        List<List<TextBox>> dataLines = allLines.subList(dataStart, endIdx);
        double[] anchors = detectAnchorsFromHeader(allLines.get(startIdx), pw);

        refineAnchorsFromData(dataLines, anchors, pw);

        return buildRecords(dataLines, anchors, pw);
    }

    // ═══════════════════════════════════════════════════════════════
    // 핵심: 레코드 빌드
    // ═══════════════════════════════════════════════════════════════

    private List<AddressInfo> buildRecords(List<List<TextBox>> dataLines, double[] anchors, int pw) {
        if (dataLines.isEmpty())
            return List.of();

        double bNo = pw * anchors[1];
        double bDate = pw * anchors[2];
        double bRel = pw * anchors[3];

        List<AddressInfo> results = new ArrayList<>();
        AddressInfo.Builder builder = new AddressInfo.Builder();
        List<TextBox> dateBuffer = new ArrayList<>();

        for (List<TextBox> line : dataLines) {
            // ── 분할 판단 ──
            boolean hasNewNo = detectNewNo(line, bNo, bDate);
            boolean lineHasDate = line.stream().anyMatch(b -> b.getCenterX() >= bDate && hasDate(b));
            boolean lineHasAddr = line.stream()
                    .anyMatch(b -> b.getCenterX() >= bNo && b.getCenterX() < bDate && !hasDate(b));
            // 날짜/관계 구역에 박스가 없는 행 = 주소만 있는 행
            boolean lineOnlyAddr = lineHasAddr && !lineHasDate
                    && line.stream().noneMatch(b -> b.getCenterX() >= bRel);

            // (a) 날짜+주소가 동시에 있으면 새 레코드
            // (b) 주소만 있는 행인데, 이전 레코드에 변동사유가 이미 채워져 있으면 새 레코드 (아랫줄만 있는 행)
            boolean shouldSplit = hasNewNo
                    || (lineHasDate && lineHasAddr && builder.hasContent())
                    || (lineOnlyAddr && builder.hasChgRsn());

            if (shouldSplit && builder.hasContent()) {
                flush(results, builder, dateBuffer);
                builder = new AddressInfo.Builder();
                dateBuffer = new ArrayList<>();
            }

            // ── 컬럼 할당 ──
            for (TextBox box : line) {
                String clean = sanitize(box.getText());
                if (clean.isEmpty() || isTermFrag(clean))
                    continue;

                builder.updateYBounds(box.getY(), box.getHeight());
                int cx = box.getCenterX();

                // 날짜 우선: bDate 이상이면서 날짜면 날짜 버퍼
                if (cx >= bDate && hasDate(box)) {
                    dateBuffer.add(box);
                    continue;
                }

                // 상/하단 판별: 레코드 내 Y좌표 범위의 중앙값 기준
                boolean isUpperTier = isUpperTier(box, builder);

                if (cx < bNo) {
                    if (isRecordNo(box))
                        builder.appendNo(clean);
                } else if (cx < bDate) {
                    String addr = extractAddrFromMergedNo(clean, builder);
                    if (!addr.isEmpty())
                        builder.appendAddr(addr);
                } else if (cx < bRel) {
                    // DATE/RSN 구역: 날짜는 위에서 처리됨.
                    // 하단 라인은 변동사유+등록상태가 병합될 수 있으므로 항상 분리 시도
                    if (!isUpperTier) {
                        splitMergedRsnSt(clean, builder, false);
                    } else {
                        builder.appendChgRsn(clean);
                    }
                } else {
                    // REL 구역: 상단=세대주및관계, 하단=등록상태
                    // 하단에서도 변동사유+등록상태가 병합될 수 있으므로 분리 시도
                    if (isUpperTier) {
                        builder.appendHhrRel(clean);
                    } else {
                        splitMergedRsnSt(clean, builder, true);
                    }
                }
            }
        }
        flush(results, builder, dateBuffer);
        return results;
    }

    // ═══════════════════════════════════════════════════════════════
    // 앵커 탐지
    // ═══════════════════════════════════════════════════════════════

    /** 헤더 행의 키워드 좌표로 앵커 초기값 설정 */
    private double[] detectAnchorsFromHeader(List<TextBox> headerLine, int pw) {
        double[] a = { 0.0, 0.05, 0.55, 0.85 };
        for (TextBox box : headerLine) {
            String t = box.getText().replaceAll("\\s+", "");
            double r = (double) box.getX() / pw;
            if (matchAny(t, "주소")) {
                a[1] = r;
            } else if (matchAny(t, "발생일", "신고일")) {
                a[2] = Math.min(a[2], r);
            } else if (matchAny(t, "세대주", "관계")) {
                a[3] = Math.min(a[3], r);
            }
        }
        return a;
    }

    /** 데이터 행의 좌표 분포로 앵커를 보정한다. */
    private void refineAnchorsFromData(List<List<TextBox>> dataLines, double[] anchors, int pw) {
        int halfPage = pw / 2;
        int minDateX = Integer.MAX_VALUE;
        int maxDateRight = 0;
        int minAddrX = Integer.MAX_VALUE;

        for (List<TextBox> line : dataLines) {
            for (TextBox box : line) {
                // 주소 시작점 보정: 좌측 절반에서 긴 한글 텍스트의 최소 X
                if (box.getX() < halfPage) {
                    String clean = box.getText().replaceAll("[^가-힣]", "");
                    if (clean.length() >= 3) {
                        minAddrX = Math.min(minAddrX, box.getX());
                    }
                    continue;
                }
                // 날짜 앵커 보정: 우측 절반의 날짜 박스
                if (hasDate(box)) {
                    minDateX = Math.min(minDateX, box.getX());
                    maxDateRight = Math.max(maxDateRight, box.getX() + box.getWidth());
                }
            }
        }

        // 주소 시작점 보정 (헤더에서 "주소" 키워드를 못 찾은 경우)
        if (minAddrX != Integer.MAX_VALUE && minAddrX > pw * anchors[1] + 50) {
            anchors[1] = (double) minAddrX / pw;
        }

        if (minDateX == Integer.MAX_VALUE) return;
        double dateR = (double) minDateX / pw;
        double relR = (double) maxDateRight / pw;
        if (Math.abs(dateR - anchors[2]) > 0.03) {
            anchors[2] = dateR;
        }
        if (relR > anchors[3] || Math.abs(relR - anchors[3]) > 0.03) {
            anchors[3] = relR;
        }
    }

    private boolean matchAny(String text, String... kws) {
        for (String k : kws)
            if (text.contains(k) || FuzzyMatcher.fuzzyMatch(text, k, FUZZY_TH))
                return true;
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // Y축 클러스터링
    // ═══════════════════════════════════════════════════════════════

    /**
     * Y축 클러스터링: 각 박스의 centerY를 기준으로 동적 허용 오차 내의 박스를 같은 행으로 병합한다.
     * 허용 오차는 박스 높이의 중앙값(median)을 기반으로 계산하여 문서별 행 높이에 적응한다.
     */
    private List<List<TextBox>> clusterByY(List<TextBox> boxes) {
        int dynamicTolerance = computeDynamicToleranceY(boxes);

        List<TextBox> sorted = boxes.stream()
                .sorted(Comparator.comparingInt(TextBox::getCenterY))
                .collect(Collectors.toList());
        List<List<TextBox>> lines = new ArrayList<>();
        List<TextBox> cur = new ArrayList<>();
        int curBaseY = 0;

        for (TextBox box : sorted) {
            if (cur.isEmpty()) {
                cur.add(box);
                curBaseY = box.getCenterY();
            } else if (Math.abs(box.getCenterY() - curBaseY) <= dynamicTolerance) {
                cur.add(box);
            } else {
                cur.sort(Comparator.comparingInt(TextBox::getX));
                lines.add(cur);
                cur = new ArrayList<>();
                cur.add(box);
                curBaseY = box.getCenterY();
            }
        }
        if (!cur.isEmpty()) {
            cur.sort(Comparator.comparingInt(TextBox::getX));
            lines.add(cur);
        }
        return lines;
    }

    /**
     * 박스 높이의 중앙값(median)을 기반으로 Y축 클러스터링 허용 오차를 동적으로 계산한다.
     * 같은 행의 박스들은 높이의 절반 이내에 있을 것이라는 가정.
     * 최소 15, 최대 50으로 클램핑하여 극단적인 값을 방지한다.
     */
    private int computeDynamicToleranceY(List<TextBox> boxes) {
        if (boxes.isEmpty()) return Y_TOLERANCE;

        List<Integer> heights = boxes.stream()
                .map(TextBox::getHeight)
                .filter(h -> h > 0)
                .sorted()
                .toList();

        if (heights.isEmpty()) return Y_TOLERANCE;

        int median = heights.get(heights.size() / 2);
        int tolerance = Math.max(15, Math.min(50, median / 2));
        return tolerance;
    }

    // ═══════════════════════════════════════════════════════════════
    // 분할/할당 유틸
    // ═══════════════════════════════════════════════════════════════

    /**
     * 박스가 현재 레코드의 상단 행에 속하는지 판별한다.
     * 레코드 내 최상단 Y좌표(topY) 기준으로 허용 오차 이내면 상단(true).
     * 아직 Y범위가 확정되지 않은 경우(첫 번째 박스)는 상단으로 간주한다.
     */
    private boolean isUpperTier(TextBox box, AddressInfo.Builder builder) {
        if (builder.getTopY() == Integer.MAX_VALUE) {
            return true;
        }
        return Math.abs(box.getY() - builder.getTopY()) <= Y_TOLERANCE;
    }

    private boolean detectNewNo(List<TextBox> line, double bNo, double bDate) {
        if (line.stream().anyMatch(b -> b.getCenterX() < bNo && isRecordNo(b)))
            return true;
        return line.stream().anyMatch(b -> {
            if (b.getCenterX() >= bNo && b.getCenterX() < bDate) {
                return sanitize(b.getText()).matches("^\\d{1,3}\\s+[가-힣].*");
            }
            return false;
        });
    }

    private void flush(List<AddressInfo> results, AddressInfo.Builder builder, List<TextBox> dateBuffer) {
        if (!builder.hasContent())
            return;
        List<String> dates = parseDates(dateBuffer);
        switch (dates.size()) {
            case 2 -> {
                builder.appendOccDt(dates.get(0));
                builder.appendDclrDt(dates.get(1));
            }
            case 1 -> {
                builder.appendOccDt("-".repeat(10));
                builder.appendDclrDt(dates.get(0));
            }
            default -> {
            }
        }
        AddressInfo item = builder.build();
        if (!item.getNo().isEmpty() || !item.getAddr().isEmpty())
            results.add(item);
    }

    // ═══════════════════════════════════════════════════════════════
    // ROI (Fallback 전용)
    // ═══════════════════════════════════════════════════════════════

    private int findStartLine(List<List<TextBox>> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String t = mergeText(lines.get(i));
            if (KW_START.stream().filter(k -> t.contains(k) || FuzzyMatcher.fuzzyMatch(t, k, FUZZY_TH)).count() >= 2)
                return i;
        }
        return -1;
    }

    private int findEndLine(List<List<TextBox>> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String t = mergeText(lines.get(i));
            if (KW_END.stream().anyMatch(k -> t.contains(k) || FuzzyMatcher.fuzzyMatch(t, k, 0.7)))
                return i;
            if (t.length() >= 2 && t.chars().allMatch(c -> TERM_SET.indexOf(c) >= 0))
                return i;
        }
        return lines.size();
    }

    // ═══════════════════════════════════════════════════════════════
    // 판별
    // ═══════════════════════════════════════════════════════════════

    private boolean isRecordNo(TextBox box) {
        String c = sanitize(box.getText());
        if (!P_NO.matcher(c).matches())
            return false;
        if (box.getText().trim().matches(".*\\d+[가-힣]+.*"))
            return false;
        try {
            return Integer.parseInt(c) <= NO_LIMIT;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String extractAddrFromMergedNo(String text, AddressInfo.Builder builder) {
        if (text.isEmpty() || builder.hasNo())
            return text;
        Matcher m = Pattern.compile("^(\\d{1,3})\\s+([가-힣].+)$").matcher(text);
        if (m.matches()) {
            try {
                if (Integer.parseInt(m.group(1)) <= NO_LIMIT) {
                    builder.appendNo(m.group(1));
                    return m.group(2).trim();
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return text;
    }

    /**
     * 날짜 패턴 판별. 텍스트가 주로 날짜로 구성된 경우만 true.
     * 긴 텍스트(주소 등)에 숫자가 포함된 경우 오탐 방지.
     */
    private boolean hasDate(TextBox box) {
        String t = box.getText();
        if (t.length() > 15 || t.length() < 6) return false;
        // 정밀 매칭: 구분자가 있는 날짜 (2019-02-01, 1996.05.21 등)
        if (P_DATE.matcher(t).find()) return true;
        // 보조 매칭: 숫자만 추출했을 때 8자리이고 연도가 유효하면 날짜 (20190201 등)
        String digits = t.replaceAll("[^0-9]", "");
        if (digits.length() == 8) {
            String yearStr = digits.substring(0, 4);
            try {
                int year = Integer.parseInt(yearStr);
                return year >= 1900 && year <= 2100;
            } catch (NumberFormatException e) { return false; }
        }
        return false;
    }

    private boolean isTermFrag(String t) {
        return t.length() == 1 && TERM_SET.contains(t);
    }

    /**
     * 헤더 하단 파편 판별 (RapidOCR 참고).
     * 번호나 날짜가 있으면 데이터 행, 데이터 키워드(전입 등)가 있으면 데이터 행,
     * 헤더 파편 키워드(변동, 사유 등)만 있으면 스킵 대상.
     */
    private boolean isHeaderFragment(List<TextBox> line) {
        if (line.isEmpty()) return true;
        // 날짜가 있으면 데이터 행
        if (line.stream().anyMatch(this::hasDate)) return false;
        // 번호가 있으면 데이터 행
        if (line.stream().anyMatch(this::isRecordNo)) return false;

        String merged = line.stream()
                .map(b -> sanitize(b.getText()))
                .collect(Collectors.joining());
        if (merged.isEmpty()) return true;

        // 데이터 키워드(전입, 거주자 등)가 포함되어 있으면 데이터 행
        if (KW_DATA_SAFE.stream().anyMatch(merged::contains)) return false;
        // 헤더 파편 키워드가 포함되어 있으면 스킵
        if (KW_HEADER_PARTS.stream().anyMatch(merged::contains)) return true;
        // 모든 박스가 4자 이하면 헤더 파편
        return line.stream().allMatch(b -> sanitize(b.getText()).length() <= 4);
    }

    // ═══════════════════════════════════════════════════════════════
    // 데이터 가공
    // ═══════════════════════════════════════════════════════════════

    private List<String> parseDates(List<TextBox> dateBoxes) {
        return dateBoxes.stream()
                .sorted(Comparator.comparingInt(TextBox::getX))
                .map(TextBox::getText)
                .map(t -> t.replaceAll("-{2,}", "-"))
                .flatMap(t -> P_DATE.matcher(t).results())
                .map(m -> String.format("%s-%s-%s", m.group(1), m.group(2), m.group(3)))
                .toList();
    }

    /**
     * 변동사유+등록상태가 병합된 텍스트를 분리한다.
     * 등록상태 키워드가 텍스트 끝에 있으면 분리하고, 없으면 fallbackToRegSt에 따라 할당한다.
     * 
     * @param text 분리 대상 텍스트
     * @param b 빌더
     * @param fallbackToRegSt true면 매칭 실패 시 regSt에, false면 chgRsn에 할당
     */
    private void splitMergedRsnSt(String text, AddressInfo.Builder b, boolean fallbackToRegSt) {
        String s = text.trim();
        for (String kw : KW_REG_STATUS) {
            if (s.length() < kw.length())
                continue;
            String tail = s.substring(s.length() - kw.length());
            boolean match = (kw.length() <= 3 && FuzzyMatcher.getDistance(tail, kw) <= 1)
                    || FuzzyMatcher.getSimilarity(tail, kw) >= FUZZY_TH;
            if (match) {
                String rsn = s.substring(0, s.length() - kw.length()).trim();
                if (!rsn.isEmpty())
                    b.appendChgRsn(rsn);
                b.appendRegSt(kw);
                return;
            }
        }
        // 매칭 실패: 호출 컨텍스트에 따라 적절한 필드에 할당
        if (fallbackToRegSt) {
            b.appendRegSt(text);
        } else {
            b.appendChgRsn(text);
        }
    }

    private String sanitize(String t) {
        return t == null ? "" : P_CLEAN.matcher(t).replaceAll("").trim();
    }

    private int estimatePageWidth(List<TextBox> boxes) {
        return boxes.stream().mapToInt(b -> b.getX() + b.getWidth()).max().orElse(2000);
    }

    private String mergeText(List<TextBox> line) {
        return line.stream().map(b -> b.getText().replaceAll("\\s+", "")).collect(Collectors.joining());
    }
}

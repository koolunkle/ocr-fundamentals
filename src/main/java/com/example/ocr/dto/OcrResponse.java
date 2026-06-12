package com.example.ocr.dto;

import java.util.List;

/**
 * OCR 처리 결과를 클라이언트에 반환하는 응답 DTO
 */
public class OcrResponse {
    private final String code;
    private final String message;
    private final OcrData data;

    private OcrResponse(String code, String message, OcrData data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 성공 응답 생성 */
    public static OcrResponse success(String id, List<OcrResult> results) {
        return new OcrResponse("200", "Success", new OcrData(id, results));
    }

    /** 오류 응답 생성 */
    public static OcrResponse error(String code, String message) {
        return new OcrResponse(code, message, null);
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public OcrData getData() {
        return data;
    }

    /** 응답 데이터 래퍼 */
    public static class OcrData {
        private final String id;
        private final List<OcrResult> result;

        public OcrData(String id, List<OcrResult> result) {
            this.id = id;
            this.result = result;
        }

        public String getId() {
            return id;
        }

        public List<OcrResult> getResult() {
            return result;
        }
    }

    /** 단일 문서의 OCR 분석 결과 */
    public static class OcrResult {
        private final String filePath;
        private final boolean cpyOfRsdrRegstYn;
        private final String issuIdntyNo;
        private final String issuDt;
        private final String issuCharg;
        private final String name;
        private final String chnchrName;
        private final String rrn;
        private final List<AddressInfo> addrList;

        private OcrResult(Builder builder) {
            this.filePath = builder.filePath;
            this.cpyOfRsdrRegstYn = builder.cpyOfRsdrRegstYn;
            this.issuIdntyNo = builder.issuIdntyNo;
            this.issuDt = builder.issuDt;
            this.issuCharg = builder.issuCharg;
            this.name = builder.name;
            this.chnchrName = builder.chnchrName;
            this.rrn = builder.rrn;
            this.addrList = builder.addrList != null ? List.copyOf(builder.addrList) : List.of();
        }

        public static Builder builder() {
            return new Builder();
        }

        public String getFilePath() {
            return filePath;
        }

        public boolean isCpyOfRsdrRegstYn() {
            return cpyOfRsdrRegstYn;
        }

        public String getIssuIdntyNo() {
            return issuIdntyNo;
        }

        public String getIssuDt() {
            return issuDt;
        }

        public String getIssuCharg() {
            return issuCharg;
        }

        public String getName() {
            return name;
        }

        public String getChnchrName() {
            return chnchrName;
        }

        public String getRrn() {
            return rrn;
        }

        public List<AddressInfo> getAddrList() {
            return addrList;
        }

        /** OcrResult 빌더 */
        public static class Builder {
            private String filePath;
            private boolean cpyOfRsdrRegstYn;
            private String issuIdntyNo;
            private String issuDt;
            private String issuCharg;
            private String name;
            private String chnchrName;
            private String rrn;
            private List<AddressInfo> addrList;

            public Builder filePath(String filePath) {
                this.filePath = filePath;
                return this;
            }

            public Builder cpyOfRsdrRegstYn(boolean cpyOfRsdrRegstYn) {
                this.cpyOfRsdrRegstYn = cpyOfRsdrRegstYn;
                return this;
            }

            public Builder issuIdntyNo(String issuIdntyNo) {
                this.issuIdntyNo = issuIdntyNo;
                return this;
            }

            public Builder issuDt(String issuDt) {
                this.issuDt = issuDt;
                return this;
            }

            public Builder issuCharg(String issuCharg) {
                this.issuCharg = issuCharg;
                return this;
            }

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            public Builder chnchrName(String chnchrName) {
                this.chnchrName = chnchrName;
                return this;
            }

            public Builder rrn(String rrn) {
                this.rrn = rrn;
                return this;
            }

            public Builder addrList(List<AddressInfo> addrList) {
                this.addrList = addrList;
                return this;
            }

            public OcrResult build() {
                return new OcrResult(this);
            }
        }
    }

    /** 주소 변동 내역 */
    public static class AddressInfo {
        private final String no;
        private final String addr;
        private final String occDt;
        private final String dclrDt;
        private final String hhrRel;
        private final String chgRsn;
        private final String regSt;

        public AddressInfo(String no, String addr, String occDt, String dclrDt,
                String hhrRel, String chgRsn, String regSt) {
            this.no = no;
            this.addr = addr;
            this.occDt = occDt;
            this.dclrDt = dclrDt;
            this.hhrRel = hhrRel;
            this.chgRsn = chgRsn;
            this.regSt = regSt;
        }

        public String getNo() {
            return no;
        }

        public String getAddr() {
            return addr;
        }

        public String getOccDt() {
            return occDt;
        }

        public String getDclrDt() {
            return dclrDt;
        }

        public String getHhrRel() {
            return hhrRel;
        }

        public String getChgRsn() {
            return chgRsn;
        }

        public String getRegSt() {
            return regSt;
        }

        /**
         * AddressInfo 조립용 빌더.
         * 컬럼별 텍스트를 누적하고 Y좌표 기반 상/하 분리를 지원.
         */
        public static class Builder {
            private final StringBuilder no = new StringBuilder();
            private final StringBuilder addr = new StringBuilder();
            private final StringBuilder occDt = new StringBuilder();
            private final StringBuilder dclrDt = new StringBuilder();
            private final StringBuilder hhrRel = new StringBuilder();
            private final StringBuilder chgRsn = new StringBuilder();
            private final StringBuilder regSt = new StringBuilder();

            private int topY = Integer.MAX_VALUE;
            private int bottomY = Integer.MIN_VALUE;

            /** Y좌표 범위 갱신 (상/하 분리 기준 계산용) */
            public void updateYBounds(int y, int height) {
                if (y < topY)
                    topY = y;
                int bottom = y + height;
                if (bottom > bottomY)
                    bottomY = bottom;
            }

            /** 셀 영역 높이의 중앙값 */
            public int getMidY() {
                return topY + (bottomY - topY) / 2;
            }

            /** 최상단 Y좌표 */
            public int getTopY() {
                return topY;
            }

            public void appendNo(String text) {
                if (no.isEmpty())
                    no.append(text.trim());
            }

            public void appendAddr(String text) {
                if (!addr.isEmpty())
                    addr.append(" ");
                addr.append(text.trim());
            }

            public void appendOccDt(String text) {
                if (occDt.isEmpty())
                    occDt.append(text.trim());
            }

            public void appendDclrDt(String text) {
                if (dclrDt.isEmpty())
                    dclrDt.append(text.trim());
            }

            public void appendHhrRel(String text) {
                if (!hhrRel.isEmpty())
                    hhrRel.append(" ");
                hhrRel.append(text.trim());
            }

            public void appendChgRsn(String text) {
                if (!chgRsn.isEmpty())
                    chgRsn.append(" ");
                chgRsn.append(text.trim());
            }

            public void appendRegSt(String text) {
                if (!regSt.isEmpty())
                    regSt.append(" ");
                regSt.append(text.trim());
            }

            /** 번호가 설정되었는지 확인 */
            public boolean hasNo() {
                return !no.isEmpty();
            }

            /** 변동사유가 설정되었는지 확인 */
            public boolean hasChgRsn() {
                return !chgRsn.isEmpty();
            }

            /** 세대주관계가 설정되었는지 확인 */
            public boolean hasHhrRel() {
                return !hhrRel.isEmpty();
            }

            /** 번호 또는 주소가 존재하는지 확인 */
            public boolean hasContent() {
                return !no.isEmpty() || !addr.isEmpty();
            }

            /** 누적된 데이터로 AddressInfo 생성 */
            public AddressInfo build() {
                return new AddressInfo(
                        no.toString().trim(), addr.toString().trim(),
                        occDt.toString().trim(), dclrDt.toString().trim(),
                        hhrRel.toString().trim(), chgRsn.toString().trim(),
                        regSt.toString().trim());
            }
        }
    }
}

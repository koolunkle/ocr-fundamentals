# KIE-OCR-SERVICE (Analysis Engine)

FastAPI 기반의 AI 문서 분석 엔진입니다. 이미지 내 텍스트를 감지(OCR)하고, 주요 항목(Key)별 데이터를 구조화(KIE)하여 추출합니다.

## 기술 스택
- Framework: FastAPI (Python 3.13)
- Package Manager: uv
- OCR Engine: PaddleOCR (v4+)
- KIE Model: LayoutXLM / ONNX Runtime (SER - Semantic Entity Recognition)
- Image Processing: OpenCV, NumPy

## API Specification

### POST /api/v1/kie/extract
이미지 파일을 업로드하여 구조화된 데이터를 추출합니다.

- Request: Multipart/Form-Data
  - file: 분석할 이미지 (JPG, PNG)
- Response: application/json
  ```json
  {
      "success": true,
      "message": "문서 분석이 완료되었습니다.",
      "data": {
          "status": "success",
          "result": {
              "doc_type": "...",
              "court": "...",
              "order": "...",
              "rqest": "...",
              "t_debtr": "..."
          }
      }
  }
  ```

## 주요 설정 (core/config.py)
환경 변수(.env)를 통해 아래 항목을 제어할 수 있습니다.
- PADDLE_LANG: OCR 언어 설정 (기본: korean)
- MAX_IMAGE_WIDTH: 성능 최적화를 위한 이미지 리사이징 기준
- ONNX_PATH: KIE 모델 파일 경로 (models/ser_model.onnx)

## 분석 파이프라인
1. Preprocessing: 입력 이미지 정규화 및 크기 조정
2. OCR Step: PaddleOCR을 통한 텍스트 영역 검출 및 인식
3. KIE Step: LayoutXLM 모델을 사용하여 추출된 텍스트 간의 관계(Entity) 분석
4. Post-processing: 추출된 필드들을 JSON 맵으로 구조화

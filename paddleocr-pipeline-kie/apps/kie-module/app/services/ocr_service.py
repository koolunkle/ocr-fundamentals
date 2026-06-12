import logging
from typing import Dict, List, Tuple

import cv2
import numpy as np
import onnxruntime as ort
from paddleocr import PaddleOCR
from transformers import AutoTokenizer

from core.config import settings

logger = logging.getLogger(__name__)


class KieOcrService:
    """학습된 ONNX 모델(LayoutXLM) 기반의 KIE(Key Information Extraction) 엔진"""

    def __init__(self):
        logger.info("[OCR-ENGINE] Synchronizing AI inference resources...")

        try:
            # 1. PaddleOCR 설정
            self.ocr = PaddleOCR(
                use_angle_cls=settings.PADDLE_USE_ANGLE_CLS,
                lang=settings.PADDLE_LANG,
                enable_mkldnn=False,
                cpu_threads=settings.PADDLE_THREADS
            )

            # 2. 토크나이저 로드 (LayoutXLM 표준)
            self.tokenizer = AutoTokenizer.from_pretrained(settings.TOKENIZER_NAME)

            # 3. ONNX 모델 로드 및 메타데이터 확보
            self._check_inference_artifacts()
            sess_options = ort.SessionOptions()
            sess_options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
            
            self.session = ort.InferenceSession(
                str(settings.ONNX_PATH.resolve()), 
                sess_options=sess_options,
                providers=['CPUExecutionProvider']
            )
            
            self.input_info = {i.name: i.shape for i in self.session.get_inputs()}
            logger.info(f"[OCR-ENGINE] Model Inputs: {list(self.input_info.keys())}")

            with open(settings.LABELS_PATH, "r", encoding="utf-8") as f:
                self.labels = [line.strip() for line in f.readlines() if line.strip()]

            logger.info(f"[OCR-ENGINE] Engine Ready. Labels: {len(self.labels)}")
        except Exception as e:
            logger.critical(f"[OCR-ENGINE] Initialization failed: {str(e)}")
            raise

    def _check_inference_artifacts(self):
        for path in [settings.ONNX_PATH, settings.LABELS_PATH]:
            if not path.exists():
                raise FileNotFoundError(f"Missing artifact: {path}")

    def extract_data(self, image_bytes: bytes) -> Dict[str, str]:
        logger.info(f"[KIE-PROCESS] Analyzing image ({len(image_bytes)} bytes)")

        try:
            img, width, height = self._standardize_image(image_bytes)

            # 1. OCR 인식: PaddleOCR을 통한 텍스트 및 좌표 추출
            result = self.ocr.ocr(img)
            if not result or not result[0]:
                logger.warning("[KIE-PROCESS] OCR detected no text.")
                return {}
            
            # PaddleOCR v3+ 호환: OCRResult 객체에서 텍스트/좌표 데이터 추출
            ocr_data = self._parse_ocr_result(result[0])
            if not ocr_data:
                logger.warning("[KIE-PROCESS] Failed to parse OCR result structure.")
                return {}

            logger.info(f"[KIE-PROCESS] OCR detected {len(ocr_data)} raw text regions.")

            # 2. LayoutXLM을 위한 데이터 추출 및 공간적 정렬
            raw_texts, raw_boxes = self._extract_and_sort_blocks(ocr_data)
            if not raw_texts:
                logger.warning("[KIE-PROCESS] No valid text blocks after filtering.")
                return {}

            logger.info(f"[KIE-PROCESS] Processing {len(raw_texts)} aligned text blocks.")
            # OCR 인식 텍스트 샘플 로깅 (디버깅용, 최대 5개)
            for idx, text in enumerate(raw_texts[:5]):
                logger.debug(f"[KIE-PROCESS]   Block[{idx}]: '{text}'")

            # 3. 텐서 변환: 토큰화 및 좌표 정규화
            tensors = self._prepare_inference_tensors(raw_texts, raw_boxes, width, height)
            logger.info(f"[KIE-PROCESS] Tensor shapes - ids: {tensors['ids'].shape}, boxes: {tensors['boxes'].shape}, mask: {tensors['mask'].shape}")

            # 4. 모델 추론: 입력 텐서를 ONNX 세션에 매핑하여 추론 실행
            input_feed = self._build_input_feed(tensors)
            logger.info(f"[KIE-PROCESS] Input feed keys: {list(input_feed.keys())}")

            outputs = self.session.run(None, input_feed)
            logits = np.asarray(outputs[0])
            logger.info(f"[KIE-PROCESS] Logits shape: {logits.shape}")
            
            if logits.ndim == 3:
                predictions = np.argmax(logits[0], axis=1)
            else:
                predictions = np.argmax(logits, axis=1)

            # 5. 예측 결과 디버깅: 라벨 분포 확인
            unique, counts = np.unique(predictions, return_counts=True)
            label_dist = {self.labels[int(u)] if int(u) < len(self.labels) else f"UNK({u})": int(c) for u, c in zip(unique, counts)}
            logger.info(f"[KIE-PROCESS] Prediction distribution: {label_dist}")

            # 6. 엔티티 재구성: BIO 태그 기반 필드 병합
            structured_data = self._reconstruct_fields(predictions, raw_texts, tensors["token_to_idx"])
            
            logger.info(f"[KIE-PROCESS] Analysis complete. Fields: {list(structured_data.keys())}")
            return structured_data

        except Exception as e:
            logger.error(f"[KIE-PROCESS] Pipeline crashed: {str(e)}", exc_info=True)
            raise RuntimeError(f"Inference Error: {str(e)}")

    def _standardize_image(self, image_bytes: bytes) -> Tuple[np.ndarray, int, int]:
        nparr = np.frombuffer(image_bytes, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        if img is None: raise ValueError("Invalid image binary.")
        
        h, w, _ = img.shape
        if w > settings.MAX_IMAGE_WIDTH:
            scale = settings.MAX_IMAGE_WIDTH / w
            img = cv2.resize(img, (int(w * scale), int(h * scale)))
            h, w, _ = img.shape
        return img, w, h

    def _parse_ocr_result(self, ocr_result) -> List:
        """PaddleOCR 버전별 결과 형식을 통일된 구조로 변환합니다.
        
        PaddleOCR v3+(3.x): OCRResult 객체 → json['res']에서 rec_texts, rec_polys 추출
        PaddleOCR v2(2.x): 리스트 형식 → [[[x1,y1],...], ("text", score)] 그대로 사용
        
        Returns:
            통일된 형식의 리스트: [([[x1,y1],[x2,y2],[x3,y3],[x4,y4]], "text"), ...]
        """
        parsed = []

        # PaddleOCR v3+: OCRResult 객체 (json 속성 보유)
        if hasattr(ocr_result, 'json'):
            try:
                res = ocr_result.json.get('res', {})
                texts = res.get('rec_texts', [])
                polys = res.get('rec_polys', [])
                
                if not texts or not polys:
                    logger.warning("[OCR-PARSE] OCRResult has no rec_texts or rec_polys.")
                    return []
                
                for text, poly in zip(texts, polys):
                    if text and poly:
                        parsed.append((poly, text))
                
                logger.info(f"[OCR-PARSE] Parsed {len(parsed)} items from OCRResult (v3+ format).")
                return parsed
            except Exception as e:
                logger.error(f"[OCR-PARSE] Failed to parse OCRResult: {e}")
                return []

        # PaddleOCR v2: 기존 리스트 형식 호환
        if isinstance(ocr_result, list):
            for item in ocr_result:
                try:
                    box = item[0]
                    text = str(item[1][0]).strip()
                    if text and box:
                        parsed.append((box, text))
                except (IndexError, TypeError, ValueError):
                    continue
            logger.info(f"[OCR-PARSE] Parsed {len(parsed)} items from list format (v2 format).")
            return parsed

        logger.warning(f"[OCR-PARSE] Unknown OCR result type: {type(ocr_result).__name__}")
        return []

    def _extract_and_sort_blocks(self, ocr_data) -> Tuple[List[str], List[List[float]]]:
        """파싱된 OCR 결과를 검증하고, 위에서 아래, 좌에서 우로 정렬합니다."""
        blocks = []
        for box, text in ocr_data:
            try:
                text = str(text).strip()
                if text and len(box) == 4:
                    # 정렬 기준을 위해 박스의 중심 좌표를 계산
                    y_center = sum([p[1] for p in box]) / 4.0
                    x_center = sum([p[0] for p in box]) / 4.0
                    blocks.append({
                        "text": text,
                        "box": box,
                        "cy": y_center,
                        "cx": x_center
                    })
            except (IndexError, TypeError, ValueError):
                continue
        
        # Y축 오차(예: 동일 줄) 허용치를 두어 정렬 (수십 픽셀 내외는 같은 줄로 취급)
        blocks.sort(key=lambda b: (round(b["cy"] / 10.0), b["cx"]))

        texts = [b["text"] for b in blocks]
        boxes = [b["box"] for b in blocks]
        return texts, boxes

    def _build_input_feed(self, tensors) -> Dict[str, np.ndarray]:
        """ONNX 모델의 입력 이름에 맞춰 텐서를 매핑합니다.
        
        PaddleOCRLabel 학습 모델은 입력 이름이 다양할 수 있으므로,
        알려진 이름 패턴을 기반으로 유연하게 매핑합니다.
        """
        # 입력 이름 → 텐서 키 매핑 규칙
        name_mapping = {
            "input_ids": "ids", "x_0": "ids",
            "bbox": "boxes", "x_1": "boxes",
            "attention_mask": "mask", "x_2": "mask",
            "token_type_ids": "ttid", "x_3": "ttid",
        }

        input_feed = {}
        for name in self.input_info.keys():
            tensor_key = name_mapping.get(name)
            if tensor_key:
                input_feed[name] = tensors[tensor_key]
            else:
                logger.warning(f"[KIE-PROCESS] Unknown model input '{name}', skipping.")

        # 모델이 요구하는 입력 중 매핑되지 않은 것이 있으면 경고
        unmapped = set(self.input_info.keys()) - set(input_feed.keys())
        if unmapped:
            logger.warning(f"[KIE-PROCESS] Unmapped model inputs: {unmapped}")

        return input_feed

    def _prepare_inference_tensors(self, texts, boxes, w, h):
        input_ids = [self.tokenizer.cls_token_id]
        bboxes = [[0, 0, 0, 0]]
        token_to_idx = [-1]

        for i, (text, box) in enumerate(zip(texts, boxes)):
            t_ids = self.tokenizer.encode(text, add_special_tokens=False)
            
            try:
                x_coords = [float(p[0]) for p in box]
                y_coords = [float(p[1]) for p in box]
                
                xmin, xmax = min(x_coords), max(x_coords)
                ymin, ymax = min(y_coords), max(y_coords)
                
                # 0 ~ 1000 정규화 (LayoutXLM 표준)
                norm_box = [
                    int(1000 * (xmin / w)), int(1000 * (ymin / h)),
                    int(1000 * (xmax / w)), int(1000 * (ymax / h))
                ]
                norm_box = [max(0, min(v, 1000)) for v in norm_box]
            except Exception:
                norm_box = [0, 0, 0, 0]

            for tid in t_ids:
                if len(input_ids) < settings.MAX_SEQUENCE_LENGTH - 1:
                    input_ids.append(tid)
                    bboxes.append(norm_box)
                    token_to_idx.append(i)

        if len(input_ids) < settings.MAX_SEQUENCE_LENGTH:
            input_ids.append(self.tokenizer.sep_token_id)
            bboxes.append([1000, 1000, 1000, 1000])
            token_to_idx.append(-1)
        else:
            # 512 범위를 넘을 경우 강제로 끝부분을 SEP 처리
            input_ids[-1] = self.tokenizer.sep_token_id
            bboxes[-1] = [1000, 1000, 1000, 1000]
            token_to_idx[-1] = -1

        # 패딩
        pad_len = settings.MAX_SEQUENCE_LENGTH - len(input_ids)
        mask = [1] * len(input_ids) + [0] * pad_len
        input_ids += [self.tokenizer.pad_token_id] * pad_len
        bboxes += [[0, 0, 0, 0]] * pad_len
        token_to_idx += [-1] * pad_len

        return {
            "ids": np.array([input_ids], dtype=np.int64),
            "boxes": np.array([bboxes], dtype=np.int64),
            "mask": np.array([mask], dtype=np.int64),
            "ttid": np.array([[0] * settings.MAX_SEQUENCE_LENGTH], dtype=np.int64),
            "token_to_idx": token_to_idx
        }

    def _reconstruct_fields(self, predictions, texts, token_to_idx) -> Dict[str, str]:
        """BIO 태그 기반으로 예측 결과를 필드별 텍스트로 재구성합니다.
        
        B-(Begin) 태그는 새 엔티티의 시작, I-(Inside) 태그는 기존 엔티티의 연속을 의미합니다.
        동일 텍스트 블록의 서브워드 토큰이 중복 추가되는 것을 방지합니다.
        """
        res = {}
        last_idx_per_entity = {}
        # 현재 활성화된 엔티티 추적 (B 태그로 시작, I 태그로 연속)
        current_entity = None

        for i, class_idx in enumerate(predictions):
            orig_idx = token_to_idx[i]
            if orig_idx == -1: 
                continue
            
            c_idx = int(class_idx)
            if c_idx >= len(self.labels): 
                continue

            label = self.labels[c_idx]
            
            # O 라벨 및 무시 대상 필터링 (원본 라벨 기준)
            if label in ["O", "o"]:
                current_entity = None
                continue

            # BIO 태그 파싱: 접두사와 엔티티 이름 분리
            if label.startswith("B-") or label.startswith("b-"):
                prefix = "B"
                entity_name = label[2:]
            elif label.startswith("I-") or label.startswith("i-"):
                prefix = "I"
                entity_name = label[2:]
            else:
                # BIO 형식이 아닌 라벨은 건너뜀
                logger.debug(f"[KIE-PROCESS] Skipping non-BIO label: {label}")
                current_entity = None
                continue

            val = str(texts[orig_idx]).strip()
            if not val:
                continue

            if prefix == "B":
                # B 태그: 새 엔티티 시작 또는 기존 엔티티에 값 추가
                current_entity = entity_name
                if entity_name not in res:
                    res[entity_name] = val
                    last_idx_per_entity[entity_name] = orig_idx
                else:
                    # 동일 엔티티의 새로운 B 태그 → 값 이어붙이기
                    if last_idx_per_entity.get(entity_name) != orig_idx:
                        res[entity_name] += " " + val
                        last_idx_per_entity[entity_name] = orig_idx
            elif prefix == "I":
                # I 태그: 현재 활성 엔티티와 일치할 때만 연속으로 처리
                if current_entity == entity_name and entity_name in res:
                    if last_idx_per_entity.get(entity_name) != orig_idx:
                        res[entity_name] += " " + val
                        last_idx_per_entity[entity_name] = orig_idx
                elif entity_name not in res:
                    # I 태그가 B 없이 등장한 경우에도 데이터 손실 방지를 위해 수용
                    logger.debug(f"[KIE-PROCESS] I-tag without preceding B-tag for '{entity_name}', accepting as new entity.")
                    current_entity = entity_name
                    res[entity_name] = val
                    last_idx_per_entity[entity_name] = orig_idx

        logger.info(f"[KIE-PROCESS] Reconstructed {len(res)} fields: {list(res.keys())}")
        return res

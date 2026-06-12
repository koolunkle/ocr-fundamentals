from pydantic_settings import BaseSettings, SettingsConfigDict
from pathlib import Path


class Settings(BaseSettings):
    """시스템 전역 설정 및 환경 변수 관리 역할"""

    # API 기본 구성 및 버전 관리
    API_V1_STR: str = "/api/v1"
    PROJECT_NAME: str = "KIE-OCR-SERVICE"

    # 모델 리소스 경로 설정 (Model Artifacts Paths)
    MODELS_DIR: Path = Path("models")
    ONNX_PATH: Path = MODELS_DIR / "ser_model.onnx"
    LABELS_PATH: Path = MODELS_DIR / "labels.txt"

    # OCR 엔진 파라미터 (Image Processing)
    MAX_IMAGE_WIDTH: int = 1200
    PADDLE_USE_ANGLE_CLS: bool = False
    PADDLE_LANG: str = "korean"
    PADDLE_THREADS: int = 4

    # NLP 모델 파라미터 (Tokenization & Inference Parameters)
    TOKENIZER_NAME: str = "xlm-roberta-base"
    MAX_SEQUENCE_LENGTH: int = 512

    # 환경 변수 파일 로드 설정 (.env 지원)
    model_config = SettingsConfigDict(env_file=".env", case_sensitive=True)


settings = Settings()

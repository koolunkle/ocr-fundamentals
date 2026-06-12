import logging
import os
import warnings

# 1. 라이브러리 경고 및 안내 메시지 차단
os.environ["PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK"] = "True"
os.environ["TRANSFORMERS_VERBOSITY"] = "error"
# 특정 경고 패턴 무시: requests 의존성 관련
warnings.filterwarnings("ignore", message=".*urllib3.*match a supported version.*")
warnings.filterwarnings("ignore", message=".*No ccache found.*")

# 2. 외부 라이브러리 로깅 노이즈 제거
for logger_name in ["httpx", "paddle", "paddlex", "huggingface_hub", "urllib3"]:
    logging.getLogger(logger_name).setLevel(logging.WARNING)

from contextlib import asynccontextmanager
from fastapi import FastAPI, status

from fastapi.responses import JSONResponse

from core.config import settings
from api.v1.api import api_router
from schemas.ocr import ErrorResponse
from services.ocr_service import KieOcrService

# 전역 로깅 구성: 타임스탬프와 모듈별 로그 레벨 가시화
logging.basicConfig(
    level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """AI 모델(OCR, KIE)의 메모리 적재 및 추론 세션의 가용성 확보"""
    logger.info(
        f"--- [SERVICE-LIFECYCLE] Bootstrapping AI Models into System Memory ---"
    )
    try:
        # OCR 인식 엔진 및 ONNX Runtime 추론 세션의 싱글톤 인스턴스화 수행
        app.state.kie_service = KieOcrService()
        yield
    except Exception as e:
        logger.critical(
            f"[SERVICE-LIFECYCLE] Critical failure during AI model loading: {str(e)}"
        )
        raise
    finally:
        logger.info(
            f"--- [SERVICE-LIFECYCLE] Releasing System Resources and Shutting Down ---"
        )


# FastAPI 애플리케이션 정의: 분석 엔진의 API 엔드포인트 및 문서화 인터페이스 설정
app = FastAPI(
    title=settings.PROJECT_NAME,
    description="AI 기반 이미지 정보 추출 및 구조화 서비스 API (OCR & LayoutXLM Engine)",
    version="1.0.0",
    lifespan=lifespan,
    docs_url="/docs",
    redoc_url="/redoc",
)

# API 버전별 라우팅 허브 통합: V1 엔드포인트 경로 할당
app.include_router(api_router, prefix=settings.API_V1_STR)


@app.exception_handler(Exception)
async def global_exception_handler(request, exc: Exception):
    """런타임 시 발생하는 예외를 포착하여 표준 에러 응답 규격으로 변환 및 복구"""
    logger.error(
        f"[GLOBAL-ERROR] Unhandled runtime exception: {str(exc)}", exc_info=True
    )

    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content=ErrorResponse(
            status="error",
            message="An unexpected internal system error occurred.",
            detail=str(exc),
        ).model_dump(),
    )


@app.get("/health", tags=["Infrastructure"], summary="시스템 가용성 체크")
async def health_check():
    """로드밸런서 및 모니터링 시스템을 위한 서비스 생존 여부 확인 인터페이스"""
    return {"status": "healthy", "service": settings.PROJECT_NAME}

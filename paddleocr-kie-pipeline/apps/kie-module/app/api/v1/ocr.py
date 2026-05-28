import logging
from fastapi import APIRouter, UploadFile, File, Depends, HTTPException, status, Request

from schemas.ocr import ExtractResponse, ErrorResponse
from services.ocr_service import KieOcrService

router = APIRouter()
logger = logging.getLogger(__name__)


def get_kie_service(request: Request) -> KieOcrService:
    """애플리케이션 상태에 등록된 AI 추론 서비스 인스턴스(Singleton) 추출 및 공유"""
    service = getattr(request.app.state, "kie_service", None)
    if not service:
        logger.error(
            "[API-GATEWAY] Request rejected: KieOcrService engine is not initialized in memory."
        )
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="The AI inference engine is currently unavailable.",
        )
    return service


@router.post(
    "/extract",
    response_model=ExtractResponse,
    responses={
        200: {"description": "OCR 인식 및 필드 추출 데이터 정상 반환"},
        400: {"model": ErrorResponse, "description": "분석 불가능한 파일 형식 업로드"},
        500: {"model": ErrorResponse, "description": "추론 세션 엔진 내부 오류"},
    },
    status_code=status.HTTP_200_OK,
    summary="이미지 텍스트 구조화 추출",
)
async def extract_document(
    file: UploadFile = File(..., description="분석할 원본 이미지 파일 (JPG/PNG)"),
    kie_service: KieOcrService = Depends(get_kie_service),
):
    try:
        logger.info(f"[API-GATEWAY] Processing request: {file.filename} (Type: {file.content_type})")
        
        image_bytes = await file.read()
        extracted_data = kie_service.extract_data(image_bytes)

        return ExtractResponse(
            status="success",
            result=extracted_data,
            message=(
                "문서 분석이 완료되었습니다."
                if extracted_data
                else "분석이 완료되었으나 식별 가능한 핵심 필드가 없습니다."
            ),
        )

    except Exception as e:
        logger.error(f"[API-GATEWAY] Extraction pipeline crashed: {str(e)}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Internal analysis engine failure: {str(e)}",
        )

from pydantic import BaseModel, Field
from typing import Dict, Optional, Any


class ExtractResponse(BaseModel):
    """OCR 정보 추출 결과 데이터 전송 객체 (DTO)"""

    status: str = Field("success", description="처리 상태 (success/error)")
    result: Optional[Dict[str, str]] = Field(
        None, description="추출된 Key-Value 데이터 세트"
    )
    message: Optional[str] = Field(None, description="결과 상세 메시지")


class ErrorResponse(BaseModel):
    """시스템 표준 에러 응답 객체"""

    status: str = Field("error", description="에러 발생 상태 고정값")
    message: str = Field(..., description="에러 요약 메시지")
    detail: Optional[Any] = Field(None, description="상세 에러 원인 및 스택 트레이스")

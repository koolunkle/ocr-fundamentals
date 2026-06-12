from fastapi import APIRouter
from api.v1 import ocr

api_router = APIRouter()
api_router.include_router(ocr.router, prefix="/kie", tags=["KIE"])

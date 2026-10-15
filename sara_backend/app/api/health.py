import time
from fastapi import APIRouter
from app.config import settings

router = APIRouter(prefix="/api/v1", tags=["Health"])
START_TIME = time.time()

@router.get("/health")
async def health_check():
    return {
        "status": "ok",
        "service": settings.APP_NAME,
        "version": settings.APP_VERSION,
        "environment": settings.APP_ENV,
        "uptime_seconds": int(time.time() - START_TIME),
        "hermes_mode": settings.HERMES_MODE
    }

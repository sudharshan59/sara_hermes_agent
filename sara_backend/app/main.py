from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.config import settings
from app.api.health import router as health_router
from app.api.tasks import router as tasks_router
from app.api.chat import router as chat_router

app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    description="FastAPI backend bridging native Android Siri APK with Hermes Agent and AWS EC2",
)

# CORS Configuration: allow requests from Android clients, emulators, local network, and web
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Mount versioned API routes
app.include_router(health_router)
app.include_router(tasks_router)
app.include_router(chat_router)

@app.get("/")
async def root():
    return {
        "name": settings.APP_NAME,
        "version": settings.APP_VERSION,
        "status": "running",
        "endpoints": [
            "/api/v1/health",
            "/api/v1/tasks",
            "/api/v1/tasks/{task_id}/stream",
            "/api/v1/chat"
        ]
    }

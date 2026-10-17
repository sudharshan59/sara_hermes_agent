import os
from pydantic_settings import BaseSettings
from typing import List

class Settings(BaseSettings):
    APP_NAME: str = "JARVIS-Hermes Assistant Backend"
    APP_VERSION: str = "1.0.0"
    APP_ENV: str = "production"
    HOST: str = "0.0.0.0"
    PORT: int = 8000

    # AWS & Deployment
    AWS_REGION: str = "us-east-1"
    CORS_ORIGINS: List[str] = ["*"]

    # Hermes & Execution Mode: 'hermes_cli', 'hermes_gateway', or 'nvidia_nim'
    HERMES_MODE: str = "auto"
    HERMES_GATEWAY_URL: str = "http://127.0.0.1:8080"
    HERMES_BIN_PATH: str = "hermes"

    # NVIDIA NIM Cloud Endpoints
    NVIDIA_API_KEY: str = ""
    NVIDIA_BASE_URL: str = "https://integrate.api.nvidia.com/v1"
    NVIDIA_DEFAULT_MODEL: str = "z-ai/glm-5.3-flash"

    # Task limits
    MAX_TASK_TIMEOUT_SECONDS: int = 120
    MAX_PAYLOAD_BYTES: int = 10 * 1024 * 1024  # 10MB

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"
        extra = "ignore"

settings = Settings()

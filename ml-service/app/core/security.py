"""Internal bearer-token guard for backend -> ml-service calls."""
from __future__ import annotations

from fastapi import Header, HTTPException, status

from app.core.config import get_settings


async def require_internal_token(authorization: str | None = Header(default=None)) -> None:
    settings = get_settings()
    if not settings.require_token:
        return
    expected = f"Bearer {settings.ml_service_token}"
    if authorization != expected:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or missing internal service token",
        )

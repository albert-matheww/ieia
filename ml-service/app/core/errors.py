"""Consistent JSON error shape, mirroring the backend envelope."""
from __future__ import annotations

import logging

from fastapi import Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

log = logging.getLogger("ieia.ml")


class MlError(Exception):
    """Raised for expected, client-facing problems (bad series, etc.)."""

    def __init__(self, message: str, code: str = "ML_BAD_REQUEST", status_code: int = 422):
        super().__init__(message)
        self.message = message
        self.code = code
        self.status_code = status_code


def _body(code: str, message: str) -> dict:
    return {"success": False, "data": None, "error": {"code": code, "message": message}}


def register_error_handlers(app) -> None:
    @app.exception_handler(MlError)
    async def _ml_error(_: Request, exc: MlError):
        return JSONResponse(status_code=exc.status_code, content=_body(exc.code, exc.message))

    @app.exception_handler(RequestValidationError)
    async def _validation(_: Request, exc: RequestValidationError):
        return JSONResponse(status_code=422, content=_body("VALIDATION_ERROR", str(exc.errors())))

    @app.exception_handler(StarletteHTTPException)
    async def _http(_: Request, exc: StarletteHTTPException):
        return JSONResponse(status_code=exc.status_code, content=_body("HTTP_ERROR", str(exc.detail)))

    @app.exception_handler(Exception)
    async def _unhandled(_: Request, exc: Exception):  # pragma: no cover - safety net
        log.exception("Unhandled ML error")
        return JSONResponse(status_code=500, content=_body("INTERNAL", "An unexpected error occurred"))

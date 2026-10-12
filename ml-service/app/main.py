"""IEIA ML service — FastAPI app.

Stateless: every endpoint is a pure function of its request body. No database
access (docs/01-architecture.md §3.3).
"""
from __future__ import annotations

import logging

from fastapi import FastAPI

from app import __version__
from app.api import routes_analytics, routes_anomaly, routes_prediction
from app.core.config import get_settings
from app.core.errors import register_error_handlers

settings = get_settings()
logging.basicConfig(level=settings.log_level, format="%(asctime)s %(levelname)s %(name)s %(message)s")

app = FastAPI(
    title="IEIA ML Service",
    version=__version__,
    description="Stateless prediction, anomaly detection and analytics for IEIA.",
)

register_error_handlers(app)
app.include_router(routes_prediction.router)
app.include_router(routes_anomaly.router)
app.include_router(routes_analytics.router)


@app.get("/health", tags=["meta"])
def health() -> dict:
    return {"status": "UP", "service": "ieia-ml", "version": __version__, "seed": settings.ml_random_seed}

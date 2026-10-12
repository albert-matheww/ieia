from __future__ import annotations

from pydantic import BaseModel, Field

from app.schemas.common import SeriesPoint

METRICS = ("CO2E", "ENERGY", "WATER", "ELECTRICITY", "FUEL", "WASTE", "TRANSPORT")
METHODS = ("ZSCORE", "IQR", "STL_RESIDUAL", "ISOLATION_FOREST")


class AnomalyRequest(BaseModel):
    # metric name -> monthly series
    series: dict[str, list[SeriesPoint]] = Field(..., description="e.g. {'ELECTRICITY': [...], 'CO2E': [...]}")
    methods: list[str] = Field(default=list(METHODS))
    sensitivity: str = Field("MEDIUM", description="LOW | MEDIUM | HIGH")


class AnomalyItem(BaseModel):
    period: str
    metric: str
    method: str
    direction: str            # SPIKE | DROP
    severity: str             # LOW | MEDIUM | HIGH
    observed: float
    baseline: float
    deviation_abs: float
    deviation_pct: float
    score: float
    methods_agreeing: int


class AnomalyResponse(BaseModel):
    anomalies: list[AnomalyItem]
    summary: dict
    seed: int

from __future__ import annotations

from pydantic import BaseModel, Field

from app.schemas.common import SeriesPoint


class PredictRequest(BaseModel):
    series: list[SeriesPoint] = Field(..., description="Monthly history, ascending by period")
    target: str = Field("CO2E", description="CO2E | ENERGY | WATER | SCORE (label only)")
    horizon: int = Field(1, ge=1, le=6, description="How many months ahead to forecast")
    min_history: int = Field(5, ge=3, description="Below this -> SeasonalNaive fallback")
    seasonality: int = Field(12, ge=2)


class ForecastPoint(BaseModel):
    period: str
    value: float
    lower: float
    upper: float


class Evaluation(BaseModel):
    mae: float | None = None
    rmse: float | None = None
    r2: float | None = None
    cv_folds: int = 0
    n_train: int = 0


class FeatureImportance(BaseModel):
    feature: str
    importance: float


class PredictResponse(BaseModel):
    target: str
    model: str
    forecast: list[ForecastPoint]
    evaluation: Evaluation
    feature_importance: list[FeatureImportance] = []
    n_history: int
    note: str
    limitations: list[str] = []
    seed: int

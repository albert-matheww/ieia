from __future__ import annotations

from fastapi import APIRouter, Depends

from app.core.config import get_settings
from app.core.security import require_internal_token
from app.prediction.forecaster import forecast
from app.schemas.prediction import PredictRequest, PredictResponse

router = APIRouter(tags=["prediction"], dependencies=[Depends(require_internal_token)])


@router.post("/predict", response_model=PredictResponse)
def predict(req: PredictRequest) -> PredictResponse:
    return forecast(req, seed=get_settings().ml_random_seed)

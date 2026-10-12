from __future__ import annotations

from fastapi import APIRouter, Depends

from app.anomaly.detector import detect
from app.core.config import get_settings
from app.core.security import require_internal_token
from app.schemas.anomaly import AnomalyRequest, AnomalyResponse

router = APIRouter(tags=["anomaly"], dependencies=[Depends(require_internal_token)])


@router.post("/anomalies", response_model=AnomalyResponse)
def anomalies(req: AnomalyRequest) -> AnomalyResponse:
    return detect(req, seed=get_settings().ml_random_seed)

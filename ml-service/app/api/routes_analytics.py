from __future__ import annotations

from fastapi import APIRouter, Depends

from app.analytics.contributors import contributions
from app.analytics.explain import explain
from app.core.security import require_internal_token
from app.schemas.analytics import (
    ContributorsRequest,
    ContributorsResponse,
    ExplainRequest,
    ExplainResponse,
)

router = APIRouter(prefix="/analytics", tags=["analytics"], dependencies=[Depends(require_internal_token)])


@router.post("/contributors", response_model=ContributorsResponse)
def contributors(req: ContributorsRequest) -> ContributorsResponse:
    return contributions(req)


@router.post("/explain", response_model=ExplainResponse)
def explain_change(req: ExplainRequest) -> ExplainResponse:
    return explain(req)

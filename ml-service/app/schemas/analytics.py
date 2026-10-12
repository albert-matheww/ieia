from __future__ import annotations

from pydantic import BaseModel, Field


class ContributorInput(BaseModel):
    category: str
    activity_code: str | None = None
    co2e_kg: float


class ContributorsRequest(BaseModel):
    impacts: list[ContributorInput]


class ContributorShare(BaseModel):
    key: str
    label: str
    co2e_kg: float
    share_pct: float


class ContributorsResponse(BaseModel):
    total_co2e_kg: float
    by_category: list[ContributorShare]
    by_activity: list[ContributorShare]
    top_contributor: ContributorShare | None


class MetricBundle(BaseModel):
    co2e: float = 0.0
    energy: float = 0.0
    water: float = 0.0
    waste: float = 0.0
    transport: float = 0.0


class ExplainRequest(BaseModel):
    current: MetricBundle
    previous: MetricBundle
    weights: dict[str, float] = Field(
        default={"carbon": 0.35, "energy": 0.20, "water": 0.15, "waste": 0.15, "transport": 0.15}
    )


class Driver(BaseModel):
    category: str
    metric: str
    change_pct: float
    points_impact: float
    direction: str


class ExplainResponse(BaseModel):
    score_delta: float
    drivers: list[Driver]

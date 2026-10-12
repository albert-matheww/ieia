"""Score-change decomposition — an independent oracle for the backend's
ScoreExplainer (docs/05 §4, docs/06 §5). Exact arithmetic, no ML."""
from __future__ import annotations

from app.schemas.analytics import Driver, ExplainRequest, ExplainResponse

# scoring category -> the raw metric it normalises + human label
_CATEGORY_METRIC = {
    "carbon": ("co2e", "CO2E"),
    "energy": ("energy", "ENERGY"),
    "water": ("water", "WATER"),
    "waste": ("waste", "WASTE_LANDFILLED"),
    "transport": ("transport", "TRANSPORT_CO2E"),
}

# Same normalisation the backend uses, so the two implementations must agree.
_BASELINES = {
    "co2e": (250.0, 1200.0),
    "energy": (1800.0, 9000.0),
    "water": (3000.0, 12000.0),
    "waste": (15.0, 90.0),
    "transport": (60.0, 400.0),
}


def _sub_score(m: float, good: float, poor: float) -> float:
    if poor <= good:
        return 100.0 if m <= good else 0.0
    return max(0.0, min(100.0, 100.0 * (poor - m) / (poor - good)))


def explain(req: ExplainRequest) -> ExplainResponse:
    cur = req.current.model_dump()
    prev = req.previous.model_dump()
    drivers: list[Driver] = []
    score_delta = 0.0

    for category, (field, label) in _CATEGORY_METRIC.items():
        good, poor = _BASELINES[field]
        m_now = float(cur.get(field, 0.0))
        m_prev = float(prev.get(field, 0.0))
        sub_now = _sub_score(m_now, good, poor)
        sub_prev = _sub_score(m_prev, good, poor)
        weight = float(req.weights.get(category, 0.0))
        points = weight * (sub_now - sub_prev)
        score_delta += points

        change_pct = ((m_now - m_prev) / m_prev * 100.0) if m_prev else (100.0 if m_now > 0 else 0.0)
        drivers.append(
            Driver(
                category=category,
                metric=label,
                change_pct=round(change_pct, 1),
                points_impact=round(points, 2),
                direction="IMPROVED" if points >= 0 else "WORSENED",
            )
        )

    drivers.sort(key=lambda d: abs(d.points_impact), reverse=True)
    return ExplainResponse(score_delta=round(score_delta, 2), drivers=drivers)

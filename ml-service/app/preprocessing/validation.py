"""Guardrails applied before any modelling (docs/05-ml-methodology.md §1.2)."""
from __future__ import annotations

from app.core.errors import MlError
from app.schemas.common import SeriesPoint


def validate_series(points: list[SeriesPoint], *, name: str = "series", min_points: int = 2) -> list[SeriesPoint]:
    if points is None or len(points) < min_points:
        raise MlError(f"{name} needs at least {min_points} points, got {0 if not points else len(points)}")

    periods = [p.period for p in points]
    if len(set(periods)) != len(periods):
        raise MlError(f"{name} contains duplicate periods")

    if any(p.value is None for p in points):
        raise MlError(f"{name} contains a null value")

    if any(p.value < 0 for p in points):
        raise MlError(f"{name} contains a negative value; environmental quantities cannot be negative")

    # ascending by period
    ordered = sorted(points, key=lambda p: p.period)
    return ordered

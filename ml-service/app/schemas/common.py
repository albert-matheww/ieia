from __future__ import annotations

from pydantic import BaseModel, Field


class SeriesPoint(BaseModel):
    period: str = Field(..., description='Month as "YYYY-MM"', pattern=r"^\d{4}-(0[1-9]|1[0-2])$")
    value: float

from __future__ import annotations

import os

import pytest

os.environ.setdefault("REQUIRE_TOKEN", "false")
os.environ.setdefault("ML_RANDOM_SEED", "42")


@pytest.fixture(scope="session")
def client():
    from fastapi.testclient import TestClient

    from app.main import app

    return TestClient(app)


def monthly_series(values: list[float], start: str = "2025-01") -> list[dict]:
    import pandas as pd

    ts = pd.date_range(start + "-01", periods=len(values), freq="MS")
    return [{"period": t.strftime("%Y-%m"), "value": float(v)} for t, v in zip(ts, values)]

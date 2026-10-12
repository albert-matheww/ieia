from __future__ import annotations

import numpy as np

from app.prediction.forecaster import forecast
from app.schemas.prediction import PredictRequest
from tests.conftest import monthly_series


def _req(values, **kw):
    return PredictRequest(series=monthly_series(values), **kw)


def test_short_history_falls_back_to_seasonal_naive_with_a_clear_note():
    res = forecast(_req([100, 110, 120]), seed=42)
    assert res.model == "SeasonalNaive"
    assert res.evaluation.cv_folds == 0
    assert "seasonal-naive" in res.note.lower()
    assert res.limitations  # always disclosed
    assert len(res.forecast) == 1


def test_trained_model_is_used_with_enough_history_and_reports_metrics():
    rng = np.random.default_rng(0)
    trend = 500 + 12 * np.arange(24)
    noise = rng.normal(0, 8, 24)
    res = forecast(_req(list(trend + noise), target="CO2E"), seed=42)

    assert res.model in {"LinearRegression", "RandomForestRegressor", "GradientBoostingRegressor"}
    assert res.evaluation.cv_folds >= 2
    assert res.evaluation.mae is not None and res.evaluation.mae >= 0
    assert res.evaluation.rmse is not None and res.evaluation.rmse >= res.evaluation.mae - 1e-6
    assert res.feature_importance and abs(sum(f.importance for f in res.feature_importance) - 1.0) < 0.05
    # a rising series should forecast above the last value, within a sane band
    assert res.forecast[0].lower <= res.forecast[0].value <= res.forecast[0].upper
    assert res.forecast[0].value > trend[-1] * 0.8


def test_forecast_is_deterministic_for_a_fixed_seed():
    values = list(500 + 12 * np.arange(20) + np.sin(np.arange(20)) * 30)
    a = forecast(_req(values), seed=7)
    b = forecast(_req(values), seed=7)
    assert a.model == b.model
    assert [p.value for p in a.forecast] == [p.value for p in b.forecast]


def test_multi_step_horizon_widens_the_interval():
    values = list(400 + 10 * np.arange(24))
    res = forecast(_req(values, horizon=3), seed=42)
    assert len(res.forecast) == 3
    w0 = res.forecast[0].upper - res.forecast[0].lower
    w2 = res.forecast[2].upper - res.forecast[2].lower
    assert w2 >= w0


def test_forecasts_are_never_negative():
    values = [50, 40, 30, 20, 10, 8, 6, 5, 4, 3, 2, 1]
    res = forecast(_req(values, horizon=3), seed=42)
    assert all(p.value >= 0 and p.lower >= 0 for p in res.forecast)

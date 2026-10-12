from __future__ import annotations

import pytest

from app.core.errors import MlError
from app.preprocessing.timeseries import (
    FEATURE_COLUMNS,
    gap_count,
    make_features,
    next_month,
    to_frame,
    training_matrix,
)
from app.preprocessing.validation import validate_series
from app.schemas.common import SeriesPoint
from tests.conftest import monthly_series


def _points(dicts):
    return [SeriesPoint(**d) for d in dicts]


def test_validate_rejects_negative_and_duplicate_and_null():
    with pytest.raises(MlError):
        validate_series(_points([{"period": "2025-01", "value": -1}, {"period": "2025-02", "value": 5}]))
    with pytest.raises(MlError):
        validate_series(_points([{"period": "2025-01", "value": 1}, {"period": "2025-01", "value": 2}]))
    with pytest.raises(MlError):
        validate_series(_points([{"period": "2025-01", "value": 1}]), min_points=2)


def test_validate_sorts_ascending():
    pts = _points([{"period": "2025-03", "value": 3}, {"period": "2025-01", "value": 1},
                   {"period": "2025-02", "value": 2}])
    out = validate_series(pts)
    assert [p.period for p in out] == ["2025-01", "2025-02", "2025-03"]


def test_to_frame_fills_interior_gaps_for_features_but_reports_them():
    pts = _points([{"period": "2025-01", "value": 10}, {"period": "2025-02", "value": 20},
                   {"period": "2025-04", "value": 40}])  # March missing
    assert gap_count(pts) == 1
    df = to_frame(pts)
    assert list(df["period"]) == ["2025-01", "2025-02", "2025-03", "2025-04"]
    assert df.loc[df["period"] == "2025-03", "y"].iloc[0] == pytest.approx(30.0)  # interpolated
    assert df["y_raw"].isna().sum() == 1  # the real gap is still visible


def test_feature_matrix_has_expected_columns_and_drops_short_lag_rows():
    pts = _points(monthly_series([100, 110, 120, 130, 140, 150, 160, 170, 180]))
    feat = make_features(to_frame(pts))
    X, y = training_matrix(feat)
    assert list(X.columns) == FEATURE_COLUMNS
    assert len(X) == 6  # first 3 rows lack a full lag window
    assert len(y) == len(X)


def test_next_month_rolls_over_year():
    assert next_month("2025-12") == "2026-01"
    assert next_month("2026-06") == "2026-07"

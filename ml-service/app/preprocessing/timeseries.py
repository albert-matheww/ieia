"""Build a clean monthly frame and engineer features (docs/05-ml-methodology.md §1)."""
from __future__ import annotations

import numpy as np
import pandas as pd

from app.schemas.common import SeriesPoint

FEATURE_COLUMNS = [
    "lag_1", "lag_2", "lag_3",
    "roll_mean_3", "roll_std_3",
    "trend_idx",
    "month_sin", "month_cos",
    "delta_1",
]


def to_frame(points: list[SeriesPoint]) -> pd.DataFrame:
    """Ordered monthly frame with interior gaps filled by linear interpolation.

    The raw gap count is returned separately by :func:`gap_count` for reporting;
    interpolation is only used to build lag features.
    """
    df = pd.DataFrame({"period": [p.period for p in points], "y": [float(p.value) for p in points]})
    df["ts"] = pd.to_datetime(df["period"] + "-01")
    df = df.sort_values("ts").set_index("ts")

    full_idx = pd.date_range(df.index.min(), df.index.max(), freq="MS")
    df = df.reindex(full_idx)
    df["y_raw"] = df["y"]
    df["y"] = df["y"].interpolate(method="linear").ffill().bfill()
    df["period"] = df.index.strftime("%Y-%m")
    df.index.name = "ts"
    return df


def gap_count(points: list[SeriesPoint]) -> int:
    if len(points) < 2:
        return 0
    idx = pd.to_datetime([p.period + "-01" for p in points])
    full = pd.date_range(idx.min(), idx.max(), freq="MS")
    return int(len(full) - len(points))


def make_features(df: pd.DataFrame) -> pd.DataFrame:
    """Attach the feature matrix. Rows without a full lag window are dropped by the caller."""
    out = df.copy()
    y = out["y"]
    out["lag_1"] = y.shift(1)
    out["lag_2"] = y.shift(2)
    out["lag_3"] = y.shift(3)
    out["roll_mean_3"] = y.shift(1).rolling(3).mean()
    out["roll_std_3"] = y.shift(1).rolling(3).std().fillna(0.0)
    out["trend_idx"] = np.arange(len(out), dtype=float)
    month = out.index.month.to_numpy(dtype=float)
    out["month_sin"] = np.sin(2 * np.pi * month / 12.0)
    out["month_cos"] = np.cos(2 * np.pi * month / 12.0)
    out["delta_1"] = y.shift(1) - y.shift(2)
    return out


def training_matrix(df_feat: pd.DataFrame) -> tuple[pd.DataFrame, pd.Series]:
    usable = df_feat.dropna(subset=FEATURE_COLUMNS + ["y"])
    return usable[FEATURE_COLUMNS].copy(), usable["y"].copy()


def next_month(period: str) -> str:
    ts = pd.to_datetime(period + "-01") + pd.offsets.MonthBegin(1)
    return ts.strftime("%Y-%m")


def future_feature_row(df_feat: pd.DataFrame, future_period: str) -> pd.DataFrame:
    """One feature row for the month immediately after the observed history."""
    y = df_feat["y"]
    ts = pd.to_datetime(future_period + "-01")
    month = float(ts.month)
    row = {
        "lag_1": y.iloc[-1],
        "lag_2": y.iloc[-2] if len(y) >= 2 else y.iloc[-1],
        "lag_3": y.iloc[-3] if len(y) >= 3 else y.iloc[-1],
        "roll_mean_3": y.iloc[-3:].mean(),
        "roll_std_3": y.iloc[-3:].std(ddof=1) if len(y) >= 2 else 0.0,
        "trend_idx": float(len(df_feat)),
        "month_sin": np.sin(2 * np.pi * month / 12.0),
        "month_cos": np.cos(2 * np.pi * month / 12.0),
        "delta_1": (y.iloc[-1] - y.iloc[-2]) if len(y) >= 2 else 0.0,
    }
    return pd.DataFrame([row], columns=FEATURE_COLUMNS).fillna(0.0)

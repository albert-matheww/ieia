"""Ensemble anomaly detection (docs/05-ml-methodology.md §3).

Robust z-score (median/MAD), IQR fence, STL residual (>=24 months) and a
multivariate Isolation Forest vote; a month is reported when >=2 methods agree
or the Isolation Forest score is beyond a hard threshold. Deterministic.
"""
from __future__ import annotations

import warnings

import numpy as np
import pandas as pd
from sklearn.ensemble import IsolationForest

from app.preprocessing.timeseries import to_frame
from app.preprocessing.validation import validate_series
from app.schemas.anomaly import AnomalyItem, AnomalyRequest, AnomalyResponse

_SENSITIVITY = {
    # z / IQR / STL thresholds, Isolation-Forest contamination, and a minimum
    # deviation-vs-baseline floor so sub-threshold monthly wobble is never an
    # "anomaly" no matter how many methods statistically flag a low-noise point.
    "LOW": {"z": 3.5, "contamination": 0.05, "min_dev_pct": 20.0},
    "MEDIUM": {"z": 3.0, "contamination": 0.08, "min_dev_pct": 15.0},
    "HIGH": {"z": 2.5, "contamination": 0.12, "min_dev_pct": 10.0},
}


def _robust_z(values: np.ndarray) -> np.ndarray:
    median = np.median(values)
    mad = np.median(np.abs(values - median))
    if mad == 0:
        std = values.std(ddof=1) if values.std(ddof=1) > 0 else 1.0
        return (values - median) / std
    return (values - median) / (1.4826 * mad)


def _iqr_flags(values: np.ndarray) -> np.ndarray:
    q1, q3 = np.percentile(values, [25, 75])
    iqr = q3 - q1
    lo, hi = q1 - 1.5 * iqr, q3 + 1.5 * iqr
    return (values < lo) | (values > hi)


def _stl_resid_z(series: pd.Series, period: int = 12) -> np.ndarray | None:
    if len(series) < 2 * period:
        return None
    try:
        from statsmodels.tsa.seasonal import STL

        with warnings.catch_warnings():
            warnings.simplefilter("ignore")
            res = STL(series, period=period, robust=True).fit()
        return _robust_z(res.resid.to_numpy())
    except Exception:  # pragma: no cover - statsmodels edge cases
        return None


def _baseline(values: np.ndarray, idx: int) -> float:
    """Trailing median (min 3 points), excluding the point under test."""
    window = values[max(0, idx - 6):idx]
    if len(window) < 3:
        window = np.delete(values, idx)
    return float(np.median(window)) if len(window) else float(values[idx])


def _severity(dev_pct: float) -> str:
    a = abs(dev_pct)
    if a >= 50:
        return "HIGH"
    if a >= 25:
        return "MEDIUM"
    return "LOW"


def detect(req: AnomalyRequest, seed: int) -> AnomalyResponse:
    sens = _SENSITIVITY.get(req.sensitivity.upper(), _SENSITIVITY["MEDIUM"])
    methods = {m.upper() for m in req.methods}

    per_metric_frames: dict[str, pd.DataFrame] = {}
    for metric, points in req.series.items():
        if not points or len(points) < 4:
            continue
        ordered = validate_series(points, name=metric, min_points=4)
        per_metric_frames[metric] = to_frame(ordered)

    # ---- multivariate Isolation Forest across metrics (aligned months) ----
    if_flags: dict[tuple[str, str], float] = {}
    if "ISOLATION_FOREST" in methods and per_metric_frames:
        aligned = pd.DataFrame({m: f["y"] for m, f in per_metric_frames.items()}).dropna()
        if len(aligned) >= 6 and aligned.shape[1] >= 1:
            feats = aligned.copy()
            for col in list(aligned.columns):
                base = aligned[col].rolling(3, min_periods=1).median()
                feats[f"{col}__ratio"] = aligned[col] / base.replace(0, np.nan)
            feats = feats.replace([np.inf, -np.inf], np.nan).fillna(1.0)
            model = IsolationForest(
                contamination=sens["contamination"], random_state=seed, n_estimators=200
            )
            model.fit(feats.to_numpy())
            raw = -model.score_samples(feats.to_numpy())  # higher = more anomalous
            thresh = np.quantile(raw, 1 - sens["contamination"])
            for ts, score in zip(feats.index, raw):
                if score >= thresh:
                    period = ts.strftime("%Y-%m")
                    for col in aligned.columns:
                        if_flags[(period, col)] = float(score)

    anomalies: list[AnomalyItem] = []
    scanned = 0
    for metric, frame in per_metric_frames.items():
        values = frame["y"].to_numpy()
        periods = frame["period"].tolist()
        scanned += len(values)

        z = _robust_z(values)
        iqr = _iqr_flags(values)
        stl_z = _stl_resid_z(frame["y"], period=12)

        for i, period in enumerate(periods):
            if pd.isna(frame["y_raw"].iloc[i]):
                continue  # interpolated gap, not a real observation
            votes: list[str] = []
            if "ZSCORE" in methods and abs(z[i]) >= sens["z"]:
                votes.append("ZSCORE")
            if "IQR" in methods and iqr[i]:
                votes.append("IQR")
            if "STL_RESIDUAL" in methods and stl_z is not None and abs(stl_z[i]) >= sens["z"]:
                votes.append("STL_RESIDUAL")
            if_hit = (period, metric) in if_flags
            if if_hit:
                votes.append("ISOLATION_FOREST")

            if len(votes) < 2 and not (if_hit and if_flags[(period, metric)] >= 0.7):
                continue

            observed = float(values[i])
            baseline = _baseline(values, i)
            dev_abs = observed - baseline
            dev_pct = (dev_abs / baseline * 100.0) if baseline else 0.0

            # A change smaller than the sensitivity floor is normal month-to-month
            # variation, not an actionable anomaly — drop it even if flagged.
            if abs(dev_pct) < sens["min_dev_pct"]:
                continue

            anomalies.append(
                AnomalyItem(
                    period=period,
                    metric=metric,
                    method="CONSENSUS" if len(votes) >= 2 else "ISOLATION_FOREST",
                    direction="SPIKE" if dev_abs >= 0 else "DROP",
                    severity=_severity(dev_pct),
                    observed=round(observed, 4),
                    baseline=round(baseline, 4),
                    deviation_abs=round(dev_abs, 4),
                    deviation_pct=round(dev_pct, 2),
                    score=round(float(abs(z[i])), 4),
                    methods_agreeing=len(votes),
                )
            )

    anomalies.sort(key=lambda a: abs(a.deviation_pct), reverse=True)
    summary = {
        "metrics_scanned": list(per_metric_frames.keys()),
        "points_scanned": scanned,
        "flagged": len(anomalies),
        "sensitivity": req.sensitivity.upper(),
        "methods": sorted(methods),
    }
    return AnomalyResponse(anomalies=anomalies, summary=summary, seed=seed)

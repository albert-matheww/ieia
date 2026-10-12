from __future__ import annotations

import numpy as np

from app.anomaly.detector import detect
from app.schemas.anomaly import AnomalyRequest
from tests.conftest import monthly_series


def _req(series_map, sensitivity="MEDIUM"):
    return AnomalyRequest(
        series={k: monthly_series(v) for k, v in series_map.items()},
        sensitivity=sensitivity,
    )


def test_injected_electricity_spike_is_detected():
    rng = np.random.default_rng(1)
    base = list(400 + rng.normal(0, 10, 18))
    base[12] = 620.0  # ~+50% spike vs ~410 baseline
    res = detect(_req({"ELECTRICITY": base}), seed=42)

    spikes = [a for a in res.anomalies if a.metric == "ELECTRICITY" and a.direction == "SPIKE"]
    assert spikes, res.summary
    top = spikes[0]
    assert top.period == "2026-01"  # 13th month from 2025-01
    assert top.deviation_pct > 25
    assert top.severity in {"MEDIUM", "HIGH"}


def test_injected_water_drop_is_detected():
    rng = np.random.default_rng(2)
    base = list(8000 + rng.normal(0, 150, 16))
    base[10] = 4200.0  # sharp drop
    res = detect(_req({"WATER": base}), seed=42)
    drops = [a for a in res.anomalies if a.metric == "WATER" and a.direction == "DROP"]
    assert drops
    assert drops[0].deviation_pct < -25


def test_clean_series_has_no_false_positives():
    rng = np.random.default_rng(3)
    clean = {
        "ELECTRICITY": list(400 + rng.normal(0, 8, 18)),
        "WATER": list(8000 + rng.normal(0, 120, 18)),
        "CO2E": list(500 + rng.normal(0, 10, 18)),
    }
    res = detect(_req(clean), seed=42)
    assert res.anomalies == []
    assert res.summary["flagged"] == 0


def test_precision_recall_on_known_anomalies():
    rng = np.random.default_rng(4)
    truth = set()
    series = {}
    for metric, mean, sd in [("ELECTRICITY", 420, 12), ("FUEL", 30, 2), ("WASTE", 40, 3)]:
        vals = list(mean + rng.normal(0, sd, 24))
        # inject 2 anomalies per metric
        vals[8] = mean * 1.6
        vals[17] = mean * 0.4
        truth.add((metric, "2025-09"))   # index 8 from 2025-01
        truth.add((metric, "2026-06"))   # index 17
        series[metric] = vals

    res = detect(_req(series, sensitivity="MEDIUM"), seed=42)
    found = {(a.metric, a.period) for a in res.anomalies}

    tp = len(found & truth)
    precision = tp / len(found) if found else 0.0
    recall = tp / len(truth)
    assert recall >= 0.66, (found, truth)
    assert precision >= 0.5, (found, truth)


def test_sensitivity_high_flags_at_least_as_many_as_low():
    rng = np.random.default_rng(5)
    vals = list(400 + rng.normal(0, 15, 20))
    vals[10] = 470  # a mild bump
    low = detect(_req({"ELECTRICITY": vals}, "LOW"), seed=42)
    high = detect(_req({"ELECTRICITY": vals}, "HIGH"), seed=42)
    assert high.summary["flagged"] >= low.summary["flagged"]

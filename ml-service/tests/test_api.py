from __future__ import annotations

import numpy as np

from tests.conftest import monthly_series


def test_health(client):
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json()["status"] == "UP"


def test_predict_endpoint_contract(client):
    values = list(500 + 12 * np.arange(18))
    r = client.post("/predict", json={"series": monthly_series(values), "target": "CO2E", "horizon": 1})
    assert r.status_code == 200
    body = r.json()
    assert set(body) >= {"target", "model", "forecast", "evaluation", "feature_importance", "note", "limitations"}
    assert len(body["forecast"]) == 1
    fp = body["forecast"][0]
    assert fp["lower"] <= fp["value"] <= fp["upper"]


def test_predict_rejects_negative_values(client):
    r = client.post("/predict", json={"series": [
        {"period": "2025-01", "value": 10}, {"period": "2025-02", "value": -5},
        {"period": "2025-03", "value": 20}]})
    assert r.status_code == 422
    assert r.json()["error"]["code"] in {"ML_BAD_REQUEST", "VALIDATION_ERROR"}


def test_anomalies_endpoint_contract(client):
    rng = np.random.default_rng(9)
    base = list(400 + rng.normal(0, 10, 16))
    base[9] = 640
    r = client.post("/anomalies", json={
        "series": {"ELECTRICITY": monthly_series(base)},
        "sensitivity": "MEDIUM",
    })
    assert r.status_code == 200
    body = r.json()
    assert "anomalies" in body and "summary" in body
    assert any(a["metric"] == "ELECTRICITY" for a in body["anomalies"])


def test_analytics_contributors(client):
    r = client.post("/analytics/contributors", json={"impacts": [
        {"category": "ELECTRICITY", "activity_code": "electricity_grid", "co2e_kg": 400},
        {"category": "TRANSPORT", "activity_code": "car_petrol", "co2e_kg": 100},
        {"category": "WASTE", "activity_code": "waste_mixed", "co2e_kg": 20},
    ]})
    assert r.status_code == 200
    body = r.json()
    assert body["total_co2e_kg"] == 520
    assert body["top_contributor"]["key"] == "ELECTRICITY"
    assert body["by_category"][0]["share_pct"] == 76.92


def test_analytics_explain_matches_backend_semantics(client):
    r = client.post("/analytics/explain", json={
        "current": {"co2e": 1050, "energy": 5200, "water": 5000, "waste": 40, "transport": 260},
        "previous": {"co2e": 900, "energy": 5000, "water": 5000, "waste": 39, "transport": 230},
    })
    assert r.status_code == 200
    body = r.json()
    # score got worse -> negative delta, carbon the biggest driver
    assert body["score_delta"] < 0
    assert body["drivers"][0]["category"] == "carbon"
    assert body["drivers"][0]["direction"] == "WORSENED"

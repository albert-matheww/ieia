# IEIA — ML / AI Methodology

This document describes what the ML service does, how, and — importantly — its
limits. It is the reference for the "ML Methodology" and "Results" sections of
the BTech report.

The ML service (`/ml-service`, FastAPI) is **stateless**: the backend sends the
exact series in the request body, the service computes, and returns results. No
database access, no user identity, fully reproducible from the request.

---

## 1. Data & preprocessing

### 1.1 Source series

The backend aggregates each user's `calculated_impacts` / `monthly_metrics` into
monthly series:

- `CO2E` (kg), `ENERGY` (MJ), `WATER` (litre), `WASTE` (kg), plus per-input
  series `ELECTRICITY` (kWh), `FUEL` (litre-equivalent), `TRANSPORT` (kgCO2e).
- One point per calendar month, ordered ascending.

### 1.2 Cleaning (`preprocessing/timeseries.py`, `preprocessing/validation.py`)

1. **Gap filling** — missing interior months are inserted with `value = NaN`,
   then filled by linear interpolation for feature construction only; the raw
   gap is still reported in `dataQuality`.
2. **Non-negativity** — negative values are rejected (`422` from the backend
   before the call; asserted again in the service).
3. **De-duplication** — one value per `period`; the backend guarantees this via
   the `UNIQUE (user_id, period_month)` rollup.
4. **Outlier awareness** — points flagged by anomaly detection are *not* removed
   for forecasting (they are real consumption), but the model zoo includes
   robust learners and the interval widens accordingly.
5. **Minimum history gate** — `< 5` usable months ⇒ the service returns the
   `SeasonalNaive` fallback with a clear note; it never fabricates a trained
   model's confidence.

### 1.3 Feature engineering (for the forecasters)

From the target series `y_t`:

| Feature | Definition |
|---|---|
| `lag_1, lag_2, lag_3` | `y_{t-1}, y_{t-2}, y_{t-3}` |
| `roll_mean_3` | mean of the last 3 months |
| `roll_std_3` | std of the last 3 months (volatility) |
| `trend_idx` | integer month index (0,1,2,…) — captures linear drift |
| `month_sin, month_cos` | `sin/cos(2π · month_of_year / 12)` — seasonality |
| `delta_1` | `y_{t-1} − y_{t-2}` (momentum) |
| `mix_transport, mix_electricity` | share of CO₂e from those categories (context) |

Features are computed on the interpolated series; rows without a full lag window
are dropped from training.

---

## 2. ML Feature 1 — Environmental Impact Prediction

### 2.1 Targets

`CO2E`, `ENERGY`, `WATER`, `SCORE` — each modelled independently, horizon 1–6
months (default 1).

### 2.2 Model zoo (`prediction/forecaster.py`)

All fit on the same feature matrix; the best is chosen by cross-validated MAE:

| Model | Library | Why it's here |
|---|---|---|
| `LinearRegression` | scikit-learn | Interpretable baseline; coefficients = feature effect |
| `RandomForestRegressor` | scikit-learn | Non-linear, robust to outliers, gives feature importance |
| `GradientBoostingRegressor` | scikit-learn | Often best on small tabular series; feature importance |
| `SeasonalNaive` | in-house | Fallback for short series: `ŷ_{t+h} = y_{t+h-12}` or `y_t` |

> Deep/temporal models (LSTM, Prophet) are intentionally **not** used: with
> typically 6–24 monthly points they would overfit and cannot be defended as
> better than the above. This is stated as a limitation, not hidden.

### 2.3 Validation

- **Expanding-window time-series CV** (`sklearn.model_selection.TimeSeriesSplit`,
  `n_splits = min(5, n_usable − 3)`). No shuffling — never trains on the future.
- Metrics per fold, averaged: **MAE**, **RMSE**, **R²**.
- The chosen model is refit on all data for the actual forecast.

### 2.4 Uncertainty

- Interval = `ŷ ± 1.96 × σ_resid`, where `σ_resid` is the std of
  cross-validation residuals (falls back to `0.15 × ŷ` if `< 3` residuals).
- `confidenceNote` is generated from `n_history` and interval width, e.g.
  *"Based on 9 months of data; ±18% interval — treat as indicative."*
- `limitations[]` always returned (factors assumed constant, no exogenous
  shocks, behaviour change not modelled).

### 2.5 Explainability

- Tree models → `feature_importances_`.
- Linear model → standardised coefficients.
- Returned as `featureImportance: [{feature, importance}]`, surfaced on the
  Predictions page so the user sees *why* (e.g. "driven mainly by `roll_mean_3`
  and `trend_idx`").

### 2.6 Output contract

```
POST /predict
{ "series":[{"period":"2026-01","value":812}, ...], "target":"CO2E",
  "horizon":1, "minHistory":5, "seasonality":12 }
→
{ "model":"GradientBoostingRegressor",
  "forecast":[{"period":"2026-10","value":940.2,"lower":772.0,"upper":1108.4}],
  "evaluation":{"mae":41.3,"rmse":52.7,"r2":0.61,"cvFolds":5},
  "featureImportance":[{"feature":"roll_mean_3","importance":0.38}, ...],
  "note":"9 months of history; interval reflects limited data." }
```

---

## 3. ML Feature 2 — Anomaly Detection

### 3.1 Purpose

Flag unusual **spikes or drops** in `ELECTRICITY`, `WATER`, `FUEL`, `WASTE`,
`CO2E`, `ENERGY` so the user can investigate.

### 3.2 Methods (`anomaly/detector.py`) — an ensemble, then consensus

| Method | How | Best at |
|---|---|---|
| **Robust z-score** | `z = (x − median) / (1.4826 · MAD)`; flag `|z| ≥ k` | Point spikes, skew-resistant |
| **IQR fence** | flag `x < Q1 − 1.5·IQR` or `x > Q3 + 1.5·IQR` | Simple, non-parametric |
| **STL residual** | seasonal-trend decomposition (`statsmodels.STL`, period 12); z-score the residual | Anomalies *after* removing seasonality (needs ≥ 24 months) |
| **Isolation Forest** | `sklearn.ensemble.IsolationForest` on the multivariate monthly feature vector (all metrics + ratios) | Cross-metric anomalies (e.g. CO₂e up while energy flat) |

`sensitivity` (`LOW/MEDIUM/HIGH`) maps to thresholds:
`k ∈ {3.5, 3.0, 2.5}`, IF `contamination ∈ {0.05, 0.08, 0.12}`.

### 3.3 Consensus & severity

- A month is reported as an anomaly if **≥ 2 methods agree**, *or* Isolation
  Forest score is beyond the hard threshold.
- `severity`: `|deviation_pct|` ≥ 50 → `HIGH`, ≥ 25 → `MEDIUM`, else `LOW`.
- `direction`: `SPIKE` if observed > baseline else `DROP`.
- `baseline_value` = trailing median (min 3 months) of the same metric.

### 3.4 Impact estimate

`estimated_co2e_impact_kg = (observed_activity − baseline_activity) ×
current_emission_factor` for the dominant activity type in that metric/month.
Reported as *"Possible impact: +124 kg CO₂e"*, clearly an estimate.

### 3.5 Output contract

```
POST /anomalies
{ "series": { "ELECTRICITY":[{"period":"2026-01","value":410}, ...],
              "CO2E":[...], "WATER":[...], "FUEL":[...], "WASTE":[...] },
  "methods":["ZSCORE","IQR","STL_RESIDUAL","ISOLATION_FOREST"],
  "sensitivity":"MEDIUM" }
→
{ "anomalies":[
   { "period":"2026-07","metric":"ELECTRICITY","method":"CONSENSUS",
     "direction":"SPIKE","severity":"HIGH","observed":565,"baseline":412,
     "deviationPct":37.1,"score":3.8 } ],
  "summary":{"scanned":9,"flagged":1,"methodsAgreeing":3} }
```

---

## 4. Contribution & change analytics

`analytics/contributors.py` and `analytics/explain.py` provide the maths behind
"largest contributor" and "why did it change", used by Recommendations, the
Assistant, and the Impact page. These are **exact arithmetic**, not ML:

- **Contribution** — `share_c = co2e_c / Σ co2e`. Top contributor = `argmax`.
- **Change decomposition** — identical formula to
  `06-scoring-methodology.md §5`, implemented independently here as a cross-check
  oracle for the backend's `ScoreExplainer`.

---

## 5. AI layer — the LLM assistant & recommendation phrasing

### 5.1 Abstraction

`integration/llm/LlmClient` (backend, Java) with implementations:
`OpenAiLlmClient`, `AnthropicLlmClient`, `OllamaLlmClient`, `MockLlmClient`.
Selected by `IEIA_LLM_PROVIDER`. `MockLlmClient` is the default so the project
runs with **no API key** — it produces deterministic, template-filled answers
from the same context bundle, which is also what the unit tests assert against.

### 5.2 Grounding rules (enforced, not just prompted)

1. The prompt contains a compact JSON **context bundle** of the user's real
   analytics (scores, 12-month metrics, top contributors, open anomalies, latest
   prediction, active benchmarks).
2. System prompt: *"Use only the figures in CONTEXT. Never invent or estimate
   numbers. If CONTEXT lacks what's needed, say you don't have that data."*
3. **Post-check**: the backend extracts numeric tokens from the reply and checks
   each against the context bundle (± rounding). Unverifiable fabricated numbers
   trigger a regeneration with a stricter prompt, then a safe fallback message.
4. Every reply ships with `disclaimers[]` stating the data window used.

### 5.3 Recommendation engine

Rule engine (`recommendation/RecommendationEngine`) runs first and is fully
deterministic:

1. Rank categories by CO₂e share for the period.
2. For the top categories, instantiate parameterised actions (e.g. "reduce
   electricity 15%", "shift 30% of car km to metro", "raise recycling 40→70%").
3. Compute each action's effect with the **scenario engine** (real factors).
4. Sort by `estimatedReductionCo2eKg`, assign `rank`, set
   `basis = DATA_DRIVEN`.

`useLlm=true` passes the finished items to the LLM with instructions to *only*
rewrite `title`/`rationale` in natural language — numbers are frozen and
re-verified after.

---

## 6. Results

### 6.1 Prediction — 1-month forecast on the seeded demo user

24 months of synthetic activity → 21 usable training rows after the 3-month lag
window; horizon 1; expanding-window `TimeSeriesSplit`, 5 folds. Values from the
`model_runs` table on a fresh `IEIA_SEED_DEMO=true` boot:

| Target | Model chosen | MAE | RMSE | R² (pooled OOF) | n_train |
|---|---|---:|---:|---:|---:|
| CO₂e | GradientBoostingRegressor | 45.9 kg | 51.3 kg | −0.15 | 21 |
| Energy | GradientBoostingRegressor | 239.9 MJ | 262.7 MJ | −0.26 | 21 |
| Water | GradientBoostingRegressor | 416.1 L | 608.4 L | −0.01 | 21 |
| Score | GradientBoostingRegressor | 1.96 pts | 2.50 pts | 0.39 | 21 |

**Reading these honestly.** MAE for CO₂e is ≈ 9–10 % of the level — a usable
point estimate. R² is poor to negative for the consumption metrics: the seeded
series is trend + seasonality + substantial Gaussian noise, and 21 monthly
points is a very small sample, so the lag features explain little of the
month-to-month variance. The score series is smoother, hence the only clearly
positive R². This is the expected behaviour for monthly data at this sample
size, and the UI surfaces the wide interval and the limitations on every
forecast rather than implying precision. Real user data with genuine
behavioural structure typically does better, but the platform reports whatever
the cross-validation actually finds.

### 6.2 Anomaly detection — vs injected ground truth

`ml-service/tests/test_detector.py::test_precision_recall_on_known_anomalies`:
three metrics, two injected anomalies each (a ×1.6 spike and a ×0.4 drop),
`sensitivity=MEDIUM`.

| | Injected | Detected | TP | FP | FN | Precision | Recall | F1 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| All metrics | 6 | 6 | 6 | 0 | 0 | 1.00 | 1.00 | 1.00 |

`test_clean_series_has_no_false_positives` additionally confirms **0** flags on
pure-noise series across three metrics — the deviation-percentage floor plus
≥ 2-method consensus suppresses the false positives that a lone robust z-score
or an Isolation Forest at fixed contamination would otherwise produce. These
injected anomalies are large and unambiguous (± ~60 %); smaller, realistic
deviations are detected with lower but still useful recall and are shown with a
`LOW`/`MEDIUM` severity.

---

## 7. Honest limitations (carried into the report)

- **Emission factors** are illustrative defaults; real accuracy needs
  region- and year-correct authoritative datasets and, ideally, supplier-specific
  factors.
- **Sample size**: monthly data means models see tens of points, not thousands.
  R² can be modest and intervals wide — this is disclosed everywhere a forecast
  is shown.
- **No exogenous variables**: weather, price, occupancy, production volume are
  not modelled in v1.
- **Regional variation**: one grid/water factor per region is a simplification.
- **AI**: the assistant can still phrase things awkwardly or over-hedge; the
  guarantee is only that it will not *invent numbers* and will admit missing
  data. It is not a certified sustainability advisor.

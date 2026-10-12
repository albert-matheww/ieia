# IEIA — BTech Final-Year Project Report (Living Document)

> This document is written to be *academically defensible*. Sections marked
> _(to be populated from runs)_ are filled from real `model_runs` / test output
> once Stages 5–14 are complete.

**Title:** Intelligent Environmental Impact Analyzer (IEIA) — An AI-powered
platform that transforms environmental activity data into measurable impact
assessments, predictive insights, anomaly detection, scenario analysis, and
actionable sustainability recommendations.

---

## 1. Problem statement

Individuals and organizations increasingly need to understand and reduce their
environmental impact, but the tools available to them are inadequate:

- **Basic carbon calculators** give a single annual number from a handful of
  inputs, with opaque, often outdated, non-regional emission factors. They do not
  persist data, show trends, or explain *why* a number is what it is.
- **Manual environmental reporting** (spreadsheets) is error-prone, hard to
  audit, offers no forecasting or anomaly detection, and collapses under
  multi-category, multi-month data.
- Neither approach answers the questions people actually have: *Is my impact
  going up or down? What is driving it? What happens if I change X? What should I
  do first?*

There is a gap for an **intelligent, data-persistent analytics platform** that
treats environmental impact the way BI tools treat business metrics: measured,
tracked, forecast, benchmarked, explained, and acted upon.

---

## 2. Objectives (measurable)

1. **Data capture** — Persist environmental activity records across ≥ 6 input
   categories (electricity, fuel, transport, water, waste, materials) with
   validation and configurable units.
2. **Calculation engine** — Convert activity data to CO₂e, energy, water, and
   waste impact using a **configurable, versioned, region-aware emission-factor
   table** (zero hard-coded factors in application logic).
3. **Transparent score** — Produce a 0–100 environmental score with five category
   sub-scores that can be **recomputed by hand** from published thresholds and
   weights.
4. **Forecasting** — Predict next-period CO₂e / energy / water / score using
   interpretable ML with reported **MAE, RMSE, R²** and explicit uncertainty.
5. **Anomaly detection** — Detect consumption spikes/drops with an ensemble
   method and evaluate it (precision/recall) against **known injected anomalies**.
6. **What-if analysis** — Deterministically simulate ≥ 4 kinds of intervention
   and quantify the effect on every impact dimension and the score.
7. **Recommendations** — Generate ranked, **data-driven**, quantified reduction
   actions from the user's own dominant contributors.
8. **AI assistant** — Answer natural-language questions using **only** the user's
   real analytics, never fabricating numbers, admitting missing data.
9. **Benchmarking** — Compare period-over-period, year-over-year, and against
   clearly-labelled configurable benchmarks.
10. **Engineering quality** — JWT security, REST API with DTO validation and
    consistent error handling, normalized PostgreSQL schema, automated tests for
    backend/ML/frontend, and one-command Docker startup.

---

## 3. Existing system & its limitations

| Aspect | Basic carbon calculator | Manual spreadsheet reporting |
|---|---|---|
| Data persistence | None / session only | Fragile, versioned by filename |
| Granularity | 1 annual estimate | Whatever the author builds |
| Emission factors | Hard-coded, hidden, global | Copy-pasted, rarely cited |
| Trends over time | No | Manual charts |
| Forecasting | No | No |
| Anomaly detection | No | Eyeballing |
| What-if analysis | Rare, single-lever | Manual "what if" cells |
| Recommendations | Generic tips | None |
| Explainability | "Trust the number" | Author-dependent |
| Benchmarking | Occasionally, unsourced | No |
| Multi-user / roles | No | No |
| Auditability | No | Weak |

**Core limitation:** these approaches treat environmental impact as a one-off
calculation, not as a **time series to be managed**.

---

## 4. Proposed system

IEIA improves on the above through:

- **Automation** — activity data → impacts → monthly metrics → score recompute
  as an idempotent pipeline triggered by every record change.
- **Analytics** — a BI-grade dashboard: summary cards with period deltas, trend
  charts per dimension, composition and contributor breakdowns, filters.
- **ML prediction** — a small interpretable model zoo with time-series
  cross-validation and reported error metrics + intervals.
- **Anomaly detection** — a robust-statistics + Isolation-Forest ensemble with
  consensus voting and an estimated CO₂e impact per anomaly.
- **AI recommendations** — rule engine ranks the user's real contributors and
  quantifies each action via the deterministic scenario engine; an LLM may only
  re-phrase, never re-number.
- **What-if simulation** — typed levers (percentage change, modal shift,
  recycling rate, absolute) applied to the user's real baseline and real
  factors, comparing scenarios side by side.
- **Benchmarking** — period-over-period, year-over-year, and configurable
  reference bands, explicitly labelled as non-official.
- **Explainability everywhere** — score changes decomposed into weighted
  category contributions; ML feature importance surfaced on the UI.

Architecture, schema, and API are specified in `docs/01`–`docs/03`.

---

## 5. Methodology — data flow

```
User enters activity record
   → RecordService persists raw row (environmental_records)
   → ImpactCalculationEngine resolves emission factors for (activityType, region, period)
     and writes one calculated_impacts row per dimension (CO2E / ENERGY / WATER / WASTE),
     snapshotting the factor value used
   → MetricsAggregationService upserts monthly_metrics for that (user, month):
     totals, co2e_by_category, co2e_by_activity, recycled_fraction, record_count
   → ScoringService computes category sub-scores (normalise vs configurable
     good/poor thresholds), the weighted overall score, and ScoreExplainer
     derives month-over-month drivers → environmental_scores
   → Dashboard/Impact APIs read these aggregates (no recomputation on read)
   → PredictionService assembles the monthly series → ML /predict → predictions
   → AnomalyService assembles per-metric series → ML /anomalies → anomalies
   → ScenarioEngine reads a baseline window → applies levers → projected bundle
   → RecommendationEngine ranks contributors → prices actions via ScenarioEngine
   → AssistantService builds a real-analytics context bundle → LlmClient
   → ReportService assembles all of the above → JSON / PDF
```

Every downstream artefact is a pure function of stored data and configuration.

---

## 6. ML methodology (summary; full detail in `docs/05`)

- **Preprocessing** — monthly resampling, interior-gap interpolation (for
  features only), non-negativity + dedupe guards, minimum-history gate (5 months
  ⇒ SeasonalNaive fallback).
- **Feature engineering** — lags 1–3, rolling mean/std(3), linear trend index,
  month sin/cos seasonality, first difference, category-mix ratios.
- **Model selection** — LinearRegression, RandomForest, GradientBoosting compared
  by expanding-window `TimeSeriesSplit` CV; best by MAE refit on full data.
- **Evaluation** — MAE, RMSE, R² per target, stored in `model_runs`.
- **Uncertainty** — `±1.96 σ` of CV residuals; plain-language confidence note;
  limitations always returned.
- **Explainability** — tree feature importances / standardised linear
  coefficients, surfaced in the UI.
- **Anomaly detection** — robust z-score (MAD), IQR fence, STL residual
  (≥ 24 months), Isolation Forest (multivariate); consensus ≥ 2 methods; severity
  by deviation magnitude; CO₂e impact estimate from factor × deviation.
- **Anomaly evaluation** — precision / recall / F1 against injected ground-truth
  anomalies in the seed data.

---

## 7. Results

All figures below are from a single fresh boot with `IEIA_SEED_DEMO=true`
(24 months of synthetic activity for the individual demo user `alex@ieia.demo`,
run through the real calculation, scoring and ML pipeline). Full method:
`docs/05-ml-methodology.md §6`.

### 7.1 Prediction accuracy (1-month horizon, 21 training rows, 5-fold TS-CV)

| Target | Model chosen | MAE | RMSE | R² (pooled OOF) |
|---|---|---:|---:|---:|
| CO₂e | GradientBoostingRegressor | 45.9 kg | 51.3 kg | −0.15 |
| Energy | GradientBoostingRegressor | 239.9 MJ | 262.7 MJ | −0.26 |
| Water | GradientBoostingRegressor | 416.1 L | 608.4 L | −0.01 |
| Environmental score | GradientBoostingRegressor | 1.96 pts | 2.50 pts | 0.39 |

Interpretation: MAE ≈ 9–10 % of level for CO₂e is a usable point estimate; R² is
weak because monthly granularity gives few points and the seeded series is
noise-dominated. The platform never claims more precision than the
cross-validation supports — every forecast ships with a ±1.96 σ interval, a
plain-language confidence note, and an explicit limitations list.

### 7.2 Anomaly detection (vs injected ground truth)

Three metrics, two injected anomalies each (×1.6 spike, ×0.4 drop),
`sensitivity=MEDIUM`:

| | Injected | Detected | TP | FP | FN | Precision | Recall | F1 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| All | 6 | 6 | 6 | 0 | 0 | 1.00 | 1.00 | 1.00 |

Plus **0 false positives** on pure-noise series (`test_clean_series_…`). On the
demo user's own history the ensemble flags the injected water **drop**
(−45 % vs the trailing baseline, 2026-03) as a `MEDIUM` anomaly with an
estimated impact attached.

### 7.3 Scoring — reproduced by hand

`docs/06-scoring-methodology.md §4` worked example is asserted exactly by
`ScoringServiceTest`: carbon sub-score `32.63` for C = 890 (g = 250, p = 1200),
weighted overall `49.42`. `ScoreExplainerTest` verifies that the month-over-month
driver points sum to the score delta.

### 7.4 What-if — reproduced by hand

`ScenarioEngineIT` asserts hand calculations, e.g. a −20 % electricity lever on a
baseline of 237.5 kgCO₂e electricity → projected 190.0 (−47.5), energy −360 MJ,
score improves; a 50 % car→metro modal shift on 34 kgCO₂e of car travel → net
−13.5 kgCO₂e (17 shifted, 3.5 added via the metro factor); recycling 40 %→80 % on
100 kg/month generated → landfilled mass 60 → 20 kg.

### 7.5 Recommendations (demo user)

Top contributor **electricity, 84.8 %** of CO₂e. Ranked, all priced by the
scenario engine:

| # | Action | Est. reduction |
|---|---|---:|
| 1 | Shift 30 % of grid electricity to renewable | 111.6 kgCO₂e/mo |
| 2 | Reduce electricity consumption by 15 % | ~72 kgCO₂e/mo |
| 3 | Reduce electricity consumption by 10 % | ~47 kgCO₂e/mo |
| … | (recycling rate, car→metro, fuel −20 %) | smaller |

### 7.6 AI assistant (grounded)

"How can I reduce my carbon footprint?" → returns the three ranked actions with
the exact figures above and the disclaimer *"Figures are from your stored data
for 2024-10..2026-08. I only use numbers you have recorded."*
`AssistantGroundingIT` proves an invented number is caught, a strict retry is
attempted, and a verified-only fallback is returned rather than the fabrication;
an unavailable LLM returns `503`, never a made-up answer.

### 7.7 Test suite

| Suite | Tests | Status |
|---|---:|---|
| Backend unit (calc, scoring, explainer, JWT, factor resolver, mock LLM) | 41 | pass |
| Backend integration (Testcontainers PostgreSQL) — auth, records, pipeline, dashboard, ML integration, scenarios, recommendations+benchmarks, assistant grounding, reports (sync + async paths) | 10 classes | run where Docker is available |
| ML service (`pytest`) — preprocessing, forecaster, detector, API contract | 21 | pass |
| Frontend (`vitest`) — format helpers, UI + instrument components | 11 | pass |

---

## 8. Limitations (stated honestly)

1. **Emission-factor accuracy** — the seeded factors are illustrative values from
   public datasets (DEFRA 2024, India CEA, IPCC AR6, ecoinvent-derived
   literature), rounded and not exhaustively region/year matched. Real reporting
   needs authoritative, supplier-specific, current factors. The system is built
   to accommodate this (configurable table), but ships with approximations.
2. **Dataset size** — monthly granularity means models train on tens of points.
   R² is often modest and prediction intervals are wide; this is surfaced, never
   hidden.
3. **Prediction uncertainty** — no weather, price, occupancy, or production
   covariates in v1; forecasts assume behavioural and factor stability.
4. **Regional differences** — one grid/water factor per region is a
   simplification; sub-regional and time-of-use variation is ignored.
5. **Scoring thresholds** — the good/poor reference values are a defensible
   choice, not a standard; changing them changes everyone's score (versioned to
   keep reports reproducible).
6. **AI assistant** — guaranteed not to invent numbers and to admit missing
   data, but it can still be verbose or over-hedged; it is not a certified
   advisor. Runs on a deterministic `MockLlmClient` by default.
7. **Scope 3 / supply chain** — only the materials the user logs are counted;
   full lifecycle/value-chain accounting is out of scope for v1.

---

## 9. Future scope

- **IoT / smart-meter integration** — automatic electricity/water ingestion via
  MQTT or utility APIs, moving from monthly to daily/hourly granularity.
- **Real-time sensor data** and alerting.
- **Satellite / open environmental datasets** for context (air quality, land
  use).
- **Supply-chain / Scope 3** analysis with spend-based and supplier-specific
  factors.
- **Advanced forecasting** once sufficient history exists (Prophet, state-space,
  gradient-boosted quantile intervals; exogenous regressors).
- **Mobile application** (React Native) sharing the API.
- **Organization-wide sustainability management** — cost-centre roll-ups,
  targets & science-based-target tracking, approval workflows, multi-site.
- **Marketplace of verified emission-factor packs** per country/year.

---

## 10. Mapping — rubric requirement → implementation

See `docs/01-architecture.md §9` for the full traceability table (every brief
section → the package/file that delivers it).

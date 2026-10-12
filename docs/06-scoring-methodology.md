# IEIA — Environmental Score Methodology (v1)

The Environmental Score is a **0–100** number where **higher is better** (lower
impact). It is deliberately a *transparent, deterministic formula* — not a
model output. Every figure the dashboard shows can be traced back through the
steps below, and the intermediate values are persisted in
`environmental_scores.score_breakdown`.

The methodology is **configurable**: all thresholds and weights live in the
active row of `scoring_configs` (seeded as version `v1`), not in code.

---

## 1. Inputs (per user, per calendar month)

From `monthly_metrics` for the `(user, period_month)`:

| Symbol | Meaning | Source |
|---|---|---|
| `C` | Total CO₂e (kg) | `total_co2e_kg` |
| `E` | Total energy (MJ) | `total_energy_mj` |
| `W` | Total water (litre) | `total_water_litre` |
| `S` | Landfilled waste (kg) | `total_waste_kg` (already net of diverted mass) |
| `r` | Recycled/diverted fraction (0–1) | `recycled_fraction` |
| `T` | Transport CO₂e (kg) | `co2e_by_category["TRANSPORT"]` |
| `H` | Headcount (organizations only) | `organizations.headcount` |

For **organizations**, each metric is divided by `H` to get a per-employee
intensity before scoring. For **individuals**, values are used as-is (they are
already per-person).

---

## 2. Step 1 — Normalise each metric to a 0–100 sub-score

For a metric value `m` with configurable thresholds `good = g` and `poor = p`
(from `scoring_configs.baselines[scope][region][METRIC]`), where `g < p`:

```
raw_sub(m) = 100 × (p − m) / (p − g)
sub(m)     = clamp(raw_sub(m), 0, 100)
```

Interpretation:
- `m ≤ g` → sub-score `100` (at or better than the "good" reference).
- `m ≥ p` → sub-score `0` (at or worse than the "poor" reference).
- In between → linear.

**Worked example** (individual, GLOBAL, carbon): `g = 250`, `p = 1200`,
observed `C = 890`:

```
raw_sub = 100 × (1200 − 890) / (1200 − 250) = 100 × 310 / 950 = 32.63
carbon_score = 32.63
```

### Seeded thresholds (v1)

| Metric | Scope | good `g` | poor `p` | Unit |
|---|---|---|---|---|
| CO₂e | INDIVIDUAL | 250 | 1200 | kgCO2e/month |
| Energy | INDIVIDUAL | 1 800 | 9 000 | MJ/month |
| Water | INDIVIDUAL | 3 000 | 12 000 | litre/month |
| Waste | INDIVIDUAL | 15 | 90 | kg landfilled/month |
| Transport | INDIVIDUAL | 60 | 400 | kgCO2e/month |
| CO₂e | ORGANIZATION | 400 | 2 000 | kgCO2e/employee/month |
| Energy | ORGANIZATION | 3 000 | 15 000 | MJ/employee/month |
| Water | ORGANIZATION | 2 000 | 10 000 | litre/employee/month |
| Waste | ORGANIZATION | 20 | 120 | kg landfilled/employee/month |
| Transport | ORGANIZATION | 100 | 600 | kgCO2e/employee/month |

> These are **illustrative reference values** chosen to place a typical user near
> the middle of the scale. They are editable by an `ADMIN` via
> `POST /api/benchmarks` / a new `scoring_configs` version, and their
> provenance is documented in `docs/07-btech-project-report.md` §Limitations.

---

## 3. Step 2 — Category sub-scores

```
carbon_score    = sub(C)                          using CO2E thresholds
energy_score    = sub(E)                          using ENERGY thresholds
water_score     = sub(W)                          using WATER thresholds
transport_score = sub(T)                          using TRANSPORT thresholds

waste_base   = sub(S)                             using WASTE thresholds
waste_bonus  = recycling_bonus_max × r            (r = recycled_fraction, max 8 pts in v1)
waste_score  = clamp(waste_base + waste_bonus, 0, 100)
```

The recycling bonus rewards diversion behaviour even when absolute landfilled
mass is already low.

---

## 4. Step 3 — Weighted overall score

```
overall = Σ_c  weight_c × category_score_c
```

Seeded weights (v1), summing to 1.0:

| Category | Weight | Rationale |
|---|---|---|
| Carbon | 0.35 | Headline climate metric; partially a roll-up of the others by design |
| Energy | 0.20 | Primary lever most users can act on |
| Water | 0.15 | Important, region-sensitive |
| Waste | 0.15 | Behavioural, includes diversion |
| Transport | 0.15 | Broken out because it is a distinct decision domain |

**Deliberate partial double-count**: transport and grid-electricity emissions
contribute to *both* `carbon_score` (via `C`) and their own category score. This
is intentional — carbon is the holistic outcome; the sibling categories measure
*specific behaviours*. It is documented rather than "corrected" so the number
stays explainable.

**Worked example** (individual): sub-scores
`carbon 32.6, energy 55.0, water 70.0, waste 62.0, transport 48.0`:

```
overall = 0.35×32.6 + 0.20×55.0 + 0.15×70.0 + 0.15×62.0 + 0.15×48.0
        = 11.41 + 11.00 + 10.50 + 9.30 + 7.20
        = 49.41  →  rounded to 49.41 (round_dp = 2)
```

---

## 5. Step 4 — Month-over-month change and drivers (explainability)

Given this month `t` and the previous month with data `t−1`:

```
Δoverall = overall_t − overall_{t−1}
         = Σ_c weight_c × (category_score_c,t − category_score_c,t−1)
```

Each category's **points impact** on the change is
`points_impact_c = weight_c × (category_score_c,t − category_score_c,t−1)`.

For each category we also compute the underlying metric change
`change_pct_c = (m_c,t − m_c,t−1) / m_c,t−1 × 100` and a direction
(`IMPROVED` if `points_impact_c > 0`, else `WORSENED`).

The API returns the drivers sorted by `|points_impact|`, e.g.:

```
Your score decreased by 8.0 points because:
  • Electricity consumption increased 18%   → carbon −4.6, energy −2.1  (worsened)
  • Transportation emissions increased 11%  → transport −1.8            (worsened)
  • Waste generation stable (+1%)           → waste +0.5                (improved)
```

This is produced by `scoring/ScoreExplainer` (backend) and cross-checked by the
ML service's `/analytics/explain` endpoint, which does the same decomposition
independently as a test oracle.

---

## 6. Edge cases

| Case | Behaviour |
|---|---|
| No records in the month | No score row written; dashboard shows an empty state |
| `< insufficient_data_months` (v1 = 2) of history | Score is shown but flagged `provisional: true`; no drivers |
| A metric has zero activity (e.g. no transport) | That metric `m = 0` → sub-score `100` for the category (nothing emitted is genuinely best) |
| Missing emission factor for a required dimension | Impact not counted; `dataQuality.hasGaps = true`; score annotated |
| Organization with `headcount` null | Falls back to INDIVIDUAL thresholds and flags `intensityBasis: "ABSOLUTE"` |

---

## 7. Why not an ML/AI score?

An opaque learned score cannot be defended in a viva, cannot be recomputed by
hand, and would drift as the model retrains. The rubric explicitly requires the
score to be *explainable rather than an unexplained AI-generated number*. ML is
used where it adds genuine value — **forecasting** the score and metrics
(Feature 1) and **detecting anomalies** in the inputs (Feature 2) — not for the
score itself.

---

## 8. Configuration surface

| What | Where | Who can change |
|---|---|---|
| Category weights | `scoring_configs.weights` | `ADMIN` (new version) |
| good/poor thresholds | `scoring_configs.baselines` | `ADMIN` (new version) |
| Recycling bonus size, rounding, provisional cutoff | `scoring_configs.scale_params` | `ADMIN` |
| Benchmark comparison bands | `benchmarks.rating_bands` | `ADMIN` |

Creating a new `scoring_configs` row with `is_active = true` (the partial unique
index guarantees only one active) and calling `POST /api/scores/recompute`
re-scores history under the new methodology. Old score rows keep their
`scoring_config_id`, so report reproducibility is preserved.

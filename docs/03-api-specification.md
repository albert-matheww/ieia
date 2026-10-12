# IEIA — REST API Specification

Base URL: `/api` · All request/response bodies are JSON · All timestamps ISO-8601 UTC.

---

## 1. Conventions

### 1.1 Response envelope

Every successful response:

```json
{ "success": true, "data": <payload>, "error": null }
```

Every error response (HTTP status set appropriately):

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "amount must be greater than 0",
    "path": "/api/environmental-records",
    "timestamp": "2026-09-08T10:11:12Z",
    "fieldErrors": [
      { "field": "amount", "message": "must be greater than 0" }
    ]
  }
}
```

Error codes: `VALIDATION_ERROR` (400), `UNAUTHENTICATED` (401), `FORBIDDEN` (403),
`NOT_FOUND` (404), `CONFLICT` (409), `UNPROCESSABLE` (422),
`ML_SERVICE_UNAVAILABLE` (503), `LLM_UNAVAILABLE` (503), `INTERNAL` (500).

### 1.2 Pagination

List endpoints accept `?page=0&size=20&sort=field,desc`. Paged payload:

```json
{ "content": [ ... ], "page": 0, "size": 20, "totalElements": 137, "totalPages": 7 }
```

### 1.3 Auth

All endpoints except `POST /api/auth/register`, `POST /api/auth/login`,
`POST /api/auth/refresh`, and `GET /actuator/health` require
`Authorization: Bearer <accessToken>`.

### 1.4 Period parameters

`period` / `from` / `to` are `YYYY-MM` (month granularity). The server pins them
to the first of the month.

---

## 2. Auth & session — `/api/auth`

| Method | Path | Auth | Body | 2xx |
|---|---|---|---|---|
| POST | `/register` | public | `RegisterRequest` | `201` `AuthResponse` |
| POST | `/login` | public | `LoginRequest` | `200` `AuthResponse` |
| POST | `/refresh` | public | `{ "refreshToken": "..." }` | `200` `AuthResponse` (rotated) |
| POST | `/logout` | bearer | `{ "refreshToken": "..." }` | `204` |
| GET  | `/me` | bearer | — | `200` `UserProfile` |

```
RegisterRequest {
  email: string (email, required),
  password: string (min 10, ≥1 letter & ≥1 digit, required),
  fullName: string (2..160, required),
  accountType: "INDIVIDUAL" | "ORGANIZATION" (required),
  organizationName?: string (required when accountType = ORGANIZATION),
  industry?: string, country?: string, region?: string, headcount?: int
}
LoginRequest { email: string, password: string }
AuthResponse {
  accessToken: string, tokenType: "Bearer", expiresInSeconds: 900,
  refreshToken: string, user: UserProfile
}
UserProfile {
  id, email, fullName, role, isActive, lastLoginAt,
  organization?: { id, name, sizeCategory, region, headcount }
}
```

Registration with `accountType = ORGANIZATION` creates the organization and makes
the user its `ORG_OWNER`; otherwise the user is a `MEMBER` with no organization.

---

## 3. Profile & organization — `/api/profile`, `/api/organization`

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/profile` | bearer | Same as `/api/auth/me` |
| PUT | `/api/profile` | bearer | `{ fullName?, country?, region? }` |
| PUT | `/api/profile/password` | bearer | `{ currentPassword, newPassword }` → `204` |
| GET | `/api/organization` | `ORG_OWNER`,`ADMIN` | Current user's org |
| PUT | `/api/organization` | `ORG_OWNER`,`ADMIN` | `{ name?, industry?, country?, region?, headcount?, sizeCategory? }` |
| GET | `/api/organization/members` | `ORG_OWNER`,`ADMIN` | Paged `UserProfile` |

---

## 4. Reference data — `/api/activity-types`, `/api/emission-factors`

Reads are available to any authenticated user; writes require `ADMIN`.

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/activity-types` | bearer | `?category=ELECTRICITY&active=true` |
| GET | `/api/activity-types/{id}` | bearer | Includes `attributeSchema`, `allowedUnits` |
| POST | `/api/activity-types` | `ADMIN` | `ActivityTypeRequest` |
| PUT | `/api/activity-types/{id}` | `ADMIN` | `ActivityTypeRequest` |
| PATCH | `/api/activity-types/{id}/active` | `ADMIN` | `{ "active": false }` |
| GET | `/api/emission-factors` | bearer | `?activityTypeId=&impactType=&region=&asOf=YYYY-MM` |
| POST | `/api/emission-factors` | `ADMIN` | `EmissionFactorRequest` |
| PUT | `/api/emission-factors/{id}` | `ADMIN` | `EmissionFactorRequest` |
| DELETE | `/api/emission-factors/{id}` | `ADMIN` | Soft-deactivates (`is_active=false`) |

```
EmissionFactorRequest {
  activityTypeId: uuid, impactType: "CO2E"|"ENERGY"|"WATER"|"WASTE",
  unitFrom: string, unitTo: "kgCO2e"|"MJ"|"litre"|"kg",
  value: number ≥ 0, region: string = "GLOBAL",
  source: string, effectiveFrom: "YYYY-MM-DD", effectiveTo?: "YYYY-MM-DD"
}
```

---

## 5. Environmental records (raw activity data) — `/api/environmental-records`

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/environmental-records` | bearer | `?from=YYYY-MM&to=YYYY-MM&category=&activityTypeId=&page=&size=&sort=` |
| GET | `/api/environmental-records/{id}` | bearer | Owner only |
| POST | `/api/environmental-records` | `MEMBER`+ | `RecordRequest` → `201` `RecordResponse` |
| POST | `/api/environmental-records/bulk` | `MEMBER`+ | `{ items: RecordRequest[] }` → `201` `{ created, failed[] }` |
| PUT | `/api/environmental-records/{id}` | `MEMBER`+ | `RecordRequest` (triggers recalculation) |
| DELETE | `/api/environmental-records/{id}` | `MEMBER`+ | `204` (triggers recalculation) |

```
RecordRequest {
  activityTypeId: uuid (required),
  period: "YYYY-MM" (required, not in the future),
  amount: number > 0 (required),
  unit: string (required, must be in activityType.allowedUnits),
  location?: string, sourceDetail?: string,
  attributes?: object,      // validated against activityType.attributeSchema
  note?: string
}
RecordResponse {
  id, activityType: { id, code, name, category },
  period, amount, unit, location, sourceDetail, attributes, note,
  impacts: [ { impactType, value, unit, emissionFactorId, factorValue, source } ],
  createdAt, updatedAt
}
```

Validation errors (`422`) include: unknown `activityTypeId`, `unit` not allowed,
future period, missing required attribute per schema, no emission factor found
for `(activityType, region, period)` — the record is still saved but the response
flags `impacts: []` with `warnings: ["NO_FACTOR:CO2E"]`.

---

## 6. Dashboard & impact — `/api/dashboard`, `/api/impact`

| Method | Path | Auth | Returns |
|---|---|---|---|
| GET | `/api/dashboard` | bearer | `DashboardResponse` for `?period=YYYY-MM` (default: latest month with data) |
| GET | `/api/impact` | bearer | `ImpactResponse` — totals + breakdowns for `?from&to` |
| GET | `/api/impact/trends` | bearer | `TrendsResponse` — monthly series for `?from&to&metrics=CO2E,ENERGY,...` |
| GET | `/api/impact/contributors` | bearer | `ContributorsResponse` — top categories/activities for `?period&by=category|activity&limit=` |
| GET | `/api/impact/explain` | bearer | `ExplainResponse` — score/metric change decomposition for `?period` vs previous |

```
DashboardResponse {
  period: "YYYY-MM",
  summary: {
    environmentalScore: { value, delta, previous },
    co2eKg:      { value, deltaPct, previous },
    energyMj:    { value, deltaPct, previous },
    waterLitre:  { value, deltaPct, previous },
    wasteKg:     { value, deltaPct, previous },
    recycledFraction: number
  },
  categoryScores: { carbon, energy, water, waste, transport },
  charts: {
    co2eTrend:        [ { period, value } ],
    energyTrend:      [ { period, value } ],
    waterTrend:       [ { period, value } ],
    scoreTrend:       [ { period, value } ],
    wasteComposition: [ { method, kg } ],
    emissionsByCategory: [ { category, kg, pct } ],
    emissionsByActivity: [ { activityCode, name, kg, pct } ]
  },
  topContributors: [ { category, activityCode, name, co2eKg, pct } ],
  openAnomalies: [ AnomalySummary ],
  latestPrediction?: PredictionSummary,
  dataQuality: { monthsOfHistory: int, recordCount: int, hasGaps: bool }
}
```

`deltaPct` compares to the immediately preceding month with data. When there is
no prior month, `previous` and `delta*` are `null` and the UI shows "—".

---

## 7. Environmental score — `/api/scores`

| Method | Path | Auth | Returns |
|---|---|---|---|
| GET | `/api/scores` | bearer | `?from&to` → `[ ScoreResponse ]` (history) |
| GET | `/api/scores/{period}` | bearer | Single `ScoreResponse` with full `breakdown` |
| GET | `/api/scores/methodology` | bearer | Active `scoring_config` (weights, baselines, formula text) |
| POST | `/api/scores/recompute` | bearer | `{ from?, to? }` → recomputes the caller's scores; `202` |

```
ScoreResponse {
  period, overall, previous, delta,
  categories: {
    carbon:    { score, rawMetric, baselineGood, baselinePoor, weight, weightedContribution },
    energy:    { ... }, water: { ... }, waste: { ... }, transport: { ... }
  },
  drivers: [ { category, metric, changePct, pointsImpact, direction } ],
  methodologyVersion: "v1"
}
```

---

## 8. Predictions — `/api/predictions`

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/predictions` | bearer | `?metric=CO2E&horizon=1` — returns latest cached or computes if stale |
| POST | `/api/predictions` | bearer | `{ metric: "CO2E"|"ENERGY"|"WATER"|"SCORE", horizon: 1..6, force?: bool }` → recompute |
| GET | `/api/predictions/history` | bearer | `?metric=` — previous predictions vs actuals (for accuracy view) |

```
PredictionResponse {
  metric, generatedForMonth, horizonMonth,
  currentValue, predictedValue, lowerBound, upperBound,
  expectedChangeAbs, expectedChangePct,
  model: { name, trainedAt, trainingRows },
  evaluation: { mae, rmse, r2, cvFolds },
  featureImportance: [ { feature, importance } ],
  confidenceNote: "Based on 9 months of history; wide interval due to limited data.",
  limitations: [ "Assumes emission factors constant", "No exogenous events modelled" ]
}
```

If history `< scoring_config.scale_params.insufficient_data_months + 3` months,
the API responds `200` with `model.name = "SeasonalNaive"` and a prominent
`confidenceNote`; it never refuses silently and never invents precision.

`503 ML_SERVICE_UNAVAILABLE` if the ML service cannot be reached.

---

## 9. Anomalies — `/api/anomalies`

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/anomalies` | bearer | `?status=OPEN&from&to&metric=&severity=` paged |
| GET | `/api/anomalies/{id}` | bearer | Includes `context` — the surrounding series window |
| POST | `/api/anomalies/scan` | bearer | `{ from?, to? }` → runs detection for the caller; `202` with count |
| PATCH | `/api/anomalies/{id}` | bearer | `{ status: "ACKNOWLEDGED"|"RESOLVED"|"DISMISSED", note? }` |

```
AnomalyResponse {
  id, period, metric, method, direction, severity,
  observedValue, baselineValue, deviationAbs, deviationPct, score,
  estimatedCo2eImpactKg, explanation, status,
  relatedRecordId?, activityCode?,
  context: { series: [ { period, value } ], baselineWindow: [from, to] }
}
```

---

## 10. What-if scenarios — `/api/scenarios`

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/scenarios` | bearer | Paged list (name, createdAt, summary deltas) |
| GET | `/api/scenarios/{id}` | bearer | Full scenario with `result` |
| POST | `/api/scenarios/simulate` | bearer | `ScenarioRequest` → `ScenarioResult` (does **not** persist) |
| POST | `/api/scenarios` | bearer | `ScenarioRequest` (persists; response includes `id`) |
| PUT | `/api/scenarios/{id}` | bearer | `ScenarioRequest` (re-simulates + persists) |
| DELETE | `/api/scenarios/{id}` | bearer | `204` |
| POST | `/api/scenarios/compare` | bearer | `{ scenarioIds: uuid[] }` → side-by-side `ComparisonResponse` |

```
ScenarioRequest {
  name: string, description?: string,
  basePeriodFrom: "YYYY-MM", basePeriodTo: "YYYY-MM",   // window averaged as baseline
  adjustments: [
    {
      target: { type: "CATEGORY"|"ACTIVITY_TYPE", value: "ELECTRICITY" | "<activityCode>" },
      change: "PCT_REDUCTION"|"PCT_INCREASE"|"MODAL_SHIFT"|"ABSOLUTE"|"RECYCLING_RATE",
      value: number,                       // e.g. 20  (%)  |  absolute kWh  |  target recycling %
      params?: { toActivityCode?: string,  // for MODAL_SHIFT (e.g. car_petrol -> metro_rail)
                 sharePct?: number }        // portion of the source activity shifted
    }
  ]
}
ScenarioResult {
  baseline: MetricBundle, projected: MetricBundle,
  deltas: { co2eKg, energyMj, waterLitre, wasteKg, overallScore } (projected - baseline),
  byCategory: [ { category, baselineCo2e, projectedCo2e, deltaCo2e } ],
  assumptions: [ "Electricity −20% applied to grid + renewable in proportion to use", ... ],
  notes: [ "Score uses the same v1 methodology; interval not modelled." ]
}
MetricBundle { co2eKg, energyMj, waterLitre, wasteKg, overallScore, categoryScores{...} }
```

The scenario engine is **deterministic arithmetic** over the user's real baseline
and the real emission factors — no ML, no randomness.

---

## 11. Recommendations — `/api/recommendations`

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/recommendations` | bearer | `?period=YYYY-MM` (default latest) → ranked list |
| POST | `/api/recommendations/generate` | bearer | `{ period?, useLlm?: bool }` → regenerates; `200` list |
| PATCH | `/api/recommendations/{id}` | bearer | `{ status: "ACCEPTED"|"IN_PROGRESS"|"DONE"|"DISMISSED" }` |

```
RecommendationResponse {
  period, generatedAt, basis: "DATA_DRIVEN"|"GENERIC", source: "RULE_ENGINE"|"LLM_ASSISTED",
  topContributor: { category, pctOfTotal },
  items: [
    {
      id, rank, category, title, rationale, actionType,
      assumedChange: { metric, changePct },
      estimatedReductionCo2eKg, estimatedReductionPct,
      estimatedOther: { energyMj?, waterLitre?, wasteKg? },
      confidence, status
    }
  ]
}
```

Rule engine first: it ranks the user's categories by CO₂e share, then emits
parameterised actions whose effect is computed with the scenario engine.
`useLlm=true` asks the LLM only to **rewrite titles/rationales more naturally**
using the already-computed numbers — it cannot change a number.

---

## 12. AI assistant — `/api/ai`

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/ai/conversations` | bearer | List |
| POST | `/api/ai/conversations` | bearer | `{ title? }` → new conversation |
| GET | `/api/ai/conversations/{id}` | bearer | Messages |
| DELETE | `/api/ai/conversations/{id}` | bearer | `204` |
| POST | `/api/ai/chat` | bearer | `{ conversationId?, message }` → `ChatResponse` |

```
ChatResponse {
  conversationId,
  reply: string,
  usedContext: {                     // exactly what was given to the model
    period, score, scoreDelta,
    topContributors: [ { category, pct } ],
    metrics12mo: [ { period, co2eKg, energyMj, waterLitre } ],
    openAnomalies: int, latestPrediction?: {...}
  },
  disclaimers: [ "Figures are from your stored data for 2026-01..2026-09.",
                 "I don't have data before 2026-01, so I can't compare to last year." ],
  model: string
}
```

System prompt guarantees (enforced + tested):
1. The model must answer **only** from `usedContext`.
2. It must **not** invent or extrapolate numeric values.
3. If context is insufficient, it must say so explicitly.
`503 LLM_UNAVAILABLE` when no provider is reachable; the UI degrades gracefully.

---

## 13. Benchmarking — `/api/benchmarks`

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/benchmarks` | bearer | Active benchmark set for the caller's scope/segment/region |
| GET | `/api/benchmarks/comparison` | bearer | `?period=YYYY-MM` → `BenchmarkComparison` |
| POST | `/api/benchmarks` | `ADMIN` | Upsert a configurable benchmark |

```
BenchmarkComparison {
  period,
  score:  { current, previousMonth, changePoints, previousYear?, changeYoY? },
  categories: [
    { category, value, unit, benchmarkValue, rating: "GOOD"|"AVERAGE"|"NEEDS_IMPROVEMENT"|"ABOVE_AVERAGE" }
  ],
  periodOverPeriod: { co2ePct, energyPct, waterPct, wastePct },
  yearOverYear?:     { co2ePct, energyPct, waterPct, wastePct },
  disclaimer: "Benchmarks are configurable reference values, not official standards."
}
```

---

## 14. Reports — `/api/reports`

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/reports` | bearer | List (title, period, status, format, size) |
| POST | `/api/reports` | bearer | `{ title?, from: "YYYY-MM", to: "YYYY-MM", format: "PDF"|"JSON", include?: string[] }` → `202` `{ id, status }` |
| GET | `/api/reports/{id}` | bearer | Metadata + `payload` (when `READY`) |
| GET | `/api/reports/{id}/download` | bearer | `application/pdf` stream (when `format=PDF` & `READY`) |
| DELETE | `/api/reports/{id}` | bearer | `204` |

`include` defaults to all sections: `["summary","scores","trends","contributors",
"anomalies","predictions","recommendations","scenarios","benchmarks","methodology"]`.
Generation is synchronous for JSON, async (status `GENERATING` → `READY`) for PDF.

---

## 15. Admin & maintenance — `/api/admin` (`ADMIN` only)

| Method | Path | Notes |
|---|---|---|
| POST | `/api/admin/recalculate` | `{ userId?, from?, to? }` — rebuild impacts→metrics→scores |
| GET | `/api/admin/model-runs` | ML training/eval history |
| GET | `/api/admin/audit` | Paged audit log |
| GET | `/actuator/health` | public liveness (also `ml-service:8000/health`) |

---

## 16. Internal — backend → ML service (`ml-service:8000`, not exposed publicly)

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/predict` | `{ series: [{period,value}], target, horizon, minHistory, seasonality: 12 }` | `{ model, forecast:[{period,value,lower,upper}], evaluation:{mae,rmse,r2,cvFolds}, featureImportance:[...], note }` |
| POST | `/anomalies` | `{ series: { CO2E:[...], ELECTRICITY:[...], WATER:[...], FUEL:[...], WASTE:[...] }, methods:["ZSCORE","IQR","STL_RESIDUAL","ISOLATION_FOREST"], sensitivity:"MEDIUM" }` | `{ anomalies:[{period,metric,method,direction,severity,observed,baseline,deviationPct,score}], summary:{...} }` |
| POST | `/analytics/contributors` | `{ impacts: [{category,activityCode,co2eKg}] }` | `{ byCategory:[...], byActivity:[...], topContributor:{...} }` |
| POST | `/analytics/explain` | `{ current: MetricBundle, previous: MetricBundle, weights }` | `{ scoreDelta, drivers:[{category,metric,changePct,pointsImpact,direction}] }` |

Header `Authorization: Bearer ${ML_SERVICE_TOKEN}` required on every call.

---

## 17. Status-code summary

| Situation | Status | `error.code` |
|---|---|---|
| Bean-validation / bad param | 400 | `VALIDATION_ERROR` |
| Missing/expired access token | 401 | `UNAUTHENTICATED` |
| Authenticated but not allowed / not owner | 403 | `FORBIDDEN` |
| Unknown id | 404 | `NOT_FOUND` |
| Duplicate email / duplicate factor slice | 409 | `CONFLICT` |
| Semantically invalid (future period, unit not allowed, no factor for required dimension) | 422 | `UNPROCESSABLE` |
| ML service down | 503 | `ML_SERVICE_UNAVAILABLE` |
| LLM provider down / unconfigured | 503 | `LLM_UNAVAILABLE` |
| Unhandled | 500 | `INTERNAL` |

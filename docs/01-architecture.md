# IEIA — System Architecture

**Intelligent Environmental Impact Analyzer**
Version 1.0 · Document owner: Engineering · Status: Baseline for implementation

---

## 1. Purpose of this document

This document is the single source of truth for the *shape* of the system: the
services, how they talk, where data lives, and which concerns belong where.
Every module implemented later must fit into this structure. Companion documents:

| Doc | Contents |
|-----|----------|
| `02-database-schema.md` | Full relational model, ERD, relationship notes |
| `03-api-specification.md` | Every REST endpoint, request/response DTOs, error format |
| `04-development-plan.md` | Stage-by-stage build order and acceptance criteria |
| `05-ml-methodology.md` | Preprocessing, features, models, evaluation, limitations |
| `06-scoring-methodology.md` | The transparent 0–100 environmental score |
| `07-btech-project-report.md` | Academic write-up (problem, objectives, results, limitations) |

---

## 2. Architectural style

A **modular, service-oriented** architecture with four deployable units plus a
database. It is deliberately *not* microservices-with-a-message-bus — that would
be over-engineered for a final-year project and hard to demo. Instead:

- **One system of record** (PostgreSQL) owned exclusively by the backend.
- **One API gateway / domain backend** (Spring Boot) that owns business rules,
  persistence, authentication and orchestration.
- **One stateless ML service** (FastAPI) that receives data *in the request*,
  computes, and returns results. It never touches the database. This keeps the
  ML tier easy to test, reason about, and redeploy.
- **One AI abstraction** living inside the backend, with pluggable LLM providers.
- **One single-page frontend** (React) that only ever talks to the Spring Boot API.

```
┌──────────────────────────────────────────────────────────────────────────┐
│                              Browser (SPA)                                │
│   React 18 + TypeScript · React Router · TanStack Query · Recharts        │
└───────────────────────────────┬──────────────────────────────────────────┘
                                │  HTTPS / JSON  (JWT in Authorization header)
                                ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                     Spring Boot 3 Backend  (port 8080)                    │
│                                                                          │
│  Web layer      Controllers · DTO validation · exception mapping          │
│  Security       JWT filter · password hashing · RBAC · CORS               │
│  Domain         auth · user · organization · activity · emissionfactor    │
│                 record · calculation · scoring · metrics · benchmark      │
│                 prediction · anomaly · scenario · recommendation ·        │
│                 assistant · report                                        │
│  Integration    ML client (REST) · LLM client (abstraction)               │
│  Persistence    Spring Data JPA · Flyway migrations                       │
└───────┬───────────────────────────────┬─────────────────────┬────────────┘
        │ JDBC                          │ REST (internal)     │ REST (internal / external)
        ▼                               ▼                     ▼
┌───────────────┐          ┌────────────────────────┐   ┌─────────────────────────┐
│  PostgreSQL   │          │  ML Service (FastAPI)   │   │   LLM Provider          │
│  port 5432    │          │  port 8000              │   │  (OpenAI | Anthropic |  │
│               │          │                         │   │   Ollama | Mock)        │
│ system of     │          │  /predict               │   │  selected by env var,   │
│ record        │          │  /anomalies             │   │  never hard-coupled     │
│               │          │  /analytics/*           │   └─────────────────────────┘
└───────────────┘          │  stateless · no DB      │
                           └────────────────────────┘
```

### Why the ML service is stateless

The backend already owns the data. If the ML service also read the DB we would
have two writers to the same schema, two sets of migrations to keep in sync, and
a much harder security story. Instead the backend assembles the exact time
series a model needs and posts it. Benefits:

- ML service has **zero** database credentials.
- Every ML call is reproducible from its request body (great for the report and
  for unit tests — fixtures are just JSON).
- The ML service can be restarted, scaled, or swapped without data-migration risk.

---

## 3. Component responsibilities

### 3.1 Frontend (`/frontend`)

| Concern | Choice | Notes |
|---|---|---|
| Framework | React 18 + Vite + TypeScript | Fast dev server, typed API models |
| Routing | React Router v6 | Protected routes via an `<AuthGuard>` |
| Server state | TanStack Query (React Query) | Caching, loading/error/empty states for free |
| Auth state | React Context + `localStorage` token | Access token in memory + refresh in httpOnly-style flow (see §6) |
| Charts | Recharts | Declarative, themeable, good for line/area/bar/pie |
| Styling | Tailwind CSS + a small design-token layer | "Serious SaaS" look; consistent spacing/typography |
| HTTP | Axios instance with interceptors | Adds `Authorization`, handles 401 → refresh → retry |
| Forms | React Hook Form + Zod | Client-side validation mirrors backend constraints |

Pages (route → purpose):

```
/login                 Login
/register              Registration
/                      Dashboard (summary cards + charts + alerts)
/data                  Environmental Data (CRUD table + entry forms)
/impact                Impact Analysis (breakdowns, contributors, explainability)
/predictions          Predictions (forecast chart, metrics, feature importance)
/anomalies            Anomaly Detection (list, severity, investigate drill-down)
/simulator            What-If Simulator (scenario builder + comparison)
/recommendations      AI Recommendations (ranked, data-driven)
/assistant            AI Assistant (chat with analytics context)
/reports              Reports (generate, preview, export PDF)
/settings             Settings / Profile / Organization
```

The frontend holds **no business logic** beyond input validation and
presentation. Every number it shows comes from an API response.

### 3.2 Backend (`/backend`) — Spring Boot 3, Java 17

Package layout under `com.ieia`:

```
config/            App config, CORS, OpenAPI, RestClient beans, ObjectMapper
common/
  dto/             ApiResponse<T>, PageResponse<T>, ErrorResponse
  exception/       ApiException hierarchy + @RestControllerAdvice
  util/            Date/period helpers, unit conversion helpers
security/          JwtService, JwtAuthFilter, SecurityConfig, PasswordEncoder cfg,
                   CurrentUser resolver, Role enum
auth/              AuthController, AuthService, RefreshToken entity/repo, DTOs
user/              User entity, UserRepository, UserService, ProfileController
organization/      Organization entity/repo/service/controller
activity/          ActivityType entity/repo/service/controller (reference data)
emissionfactor/    EmissionFactor entity/repo/service/controller (configurable factors)
record/            EnvironmentalRecord entity/repo/service/controller (raw activity data)
calculation/       ImpactCalculationEngine, CalculatedImpact entity/repo,
                   FactorResolver, recalculation orchestration
scoring/           ScoringService, EnvironmentalScore entity/repo, ScoringConfig,
                   ScoreExplainer (drivers)
metrics/           MonthlyMetric entity/repo, MetricsAggregationService,
                   DashboardController, ImpactController (trends/breakdowns)
benchmark/         Benchmark entity/repo/service/controller, BenchmarkingService
prediction/        PredictionController, PredictionService (calls ML), Prediction
                   entity/repo
anomaly/           AnomalyController, AnomalyService (calls ML), Anomaly entity/repo
scenario/          ScenarioController, ScenarioEngine (what-if math), Scenario
                   entity/repo
recommendation/    RecommendationController, RecommendationEngine (rules) +
                   optional LLM refinement, Recommendation entity/repo
assistant/         AiAssistantController, AssistantService, ConversationContext
                   builder, AiConversation/AiMessage entities/repos
report/            ReportController, ReportService, ReportPayloadBuilder,
                   PdfRenderer, Report entity/repo
integration/
  ml/              MlServiceClient (interface) + RestMlServiceClient impl, ML DTOs
  llm/             LlmClient (interface), OpenAiLlmClient, AnthropicLlmClient,
                   OllamaLlmClient, MockLlmClient, LlmClientFactory, prompt builder
```

Layering rule inside every domain package: **Controller → Service → Repository**.
Controllers never touch repositories directly. Services never return entities to
the web layer — always DTOs. Cross-domain calls go Service → Service.

### 3.3 ML service (`/ml-service`) — FastAPI, Python 3.11+

```
app/
  main.py              FastAPI app, router registration, health check, CORS
  core/
    config.py          Settings (pydantic-settings), env-driven
    logging.py         Structured logging
    errors.py          Exception handlers → consistent JSON error shape
  schemas/
    prediction.py      Pydantic request/response models
    anomaly.py
    analytics.py
  preprocessing/
    timeseries.py      Build monthly frame, gap-fill, feature engineering
    validation.py      Guardrails: min points, non-negative, dedupe
  prediction/
    forecaster.py      Model zoo (Linear/RF/GBR + SeasonalNaive fallback),
                       TimeSeriesSplit CV, interval estimation, feature importance
  anomaly/
    detector.py        Robust z-score, IQR, STL residual, IsolationForest, consensus
  analytics/
    contributors.py    Contribution analysis (share of impact by category/activity)
    explain.py         Score/prediction change decomposition helpers
  api/
    routes_prediction.py    POST /predict
    routes_anomaly.py       POST /anomalies
    routes_analytics.py     POST /analytics/contributors, /analytics/explain
tests/
  test_forecaster.py
  test_detector.py
  test_preprocessing.py
  fixtures/*.json
```

The ML service exposes pure functions of its input. No auth beyond a shared
internal token (`ML_SERVICE_TOKEN`) checked in a dependency, because it is only
reachable from the backend on the internal Docker network.

### 3.4 Database (`/database`)

PostgreSQL 16. Schema and reference data are managed by **Flyway** migrations
that live in `backend/src/main/resources/db/migration` (the canonical copy) and
are mirrored to `/database/migrations` for standalone/CI use. Demo/transactional
seed data is a separate, optional script (`/database/seed/demo_data.sql`) so a
clean analytical database and a demo database are both easy to produce.

---

## 4. Request lifecycles (the important flows)

### 4.1 Creating an environmental record → impact → metrics → score

```
POST /api/environmental-records
      │
      ▼
RecordController.create
      │ validate DTO (amount > 0, unit ∈ activityType.allowedUnits, period not future)
      ▼
RecordService.create ── saves EnvironmentalRecord
      │
      ▼
ImpactCalculationEngine.calculateForRecord(record)
      │ FactorResolver picks the EmissionFactor rows effective for
      │ (activityType, region, period) — one per impact dimension
      │ CalculatedImpact rows written: CO2E, ENERGY, WATER, WASTE as applicable
      ▼
MetricsAggregationService.recomputeMonth(userId, periodMonth)
      │ upserts MonthlyMetric (totals + co2e_by_category + record_count)
      ▼
ScoringService.recomputeMonth(userId, periodMonth)
      │ category sub-scores → weighted overall → ScoreExplainer computes drivers
      │ upserts EnvironmentalScore (with previous_score, delta, drivers jsonb)
      ▼
201 Created  { record, impacts[], monthImpactDelta }
```

Recalculation is **idempotent** and **month-scoped**: editing or deleting a
record recomputes exactly the affected `(user, month)` slice. A nightly job (or
a manual `POST /api/admin/recalculate`) can rebuild everything if factors change.

### 4.2 Prediction

```
GET /api/predictions?metric=CO2E&horizon=1
      │
      ▼
PredictionService.getOrCreate
      │ MetricsAggregationService → monthly series for the user
      │ if series unchanged since last prediction → return cached Prediction row
      ▼
MlServiceClient.predict(PredictRequest{ series, target, horizon, config })
      │  HTTP POST http://ml-service:8000/predict
      ▼
FastAPI forecaster: feature engineering → CV over model zoo → best model →
      point forecast + interval + feature importance + {mae,rmse,r2}
      ▼
PredictionService persists Prediction (+ model_runs row) → returns DTO
```

If the ML service is unreachable, the backend returns a `503` with a clear
message and the UI shows a degraded state — it never fabricates a forecast.

### 4.3 AI Assistant

```
POST /api/ai/chat { conversationId?, message }
      │
      ▼
AssistantService
      │ 1. ConversationContext builder pulls the user's REAL analytics:
      │      latest scores + deltas, 12-month metric series, top contributors,
      │      open anomalies, latest predictions, active benchmarks
      │ 2. Serialize context to compact JSON
      │ 3. LlmClient (provider from IEIA_LLM_PROVIDER) with a system prompt that
      │    FORBIDS inventing numbers and REQUIRES "insufficient data" honesty
      │ 4. Persist AiMessage(user) + AiMessage(assistant, context_snapshot)
      ▼
200 { conversationId, reply, usedContext: {...}, disclaimers: [...] }
```

The LLM only ever *phrases* answers over numbers we computed. Numeric claims in
the reply are cross-checked against the context bundle where feasible.

---

## 5. Cross-cutting concerns

| Concern | Where handled | How |
|---|---|---|
| **Validation** | Web layer (Jakarta Bean Validation on DTOs) + DB constraints | Two layers; DB is the last line |
| **Error shape** | `@RestControllerAdvice` → `ErrorResponse` | `{ timestamp, status, code, message, path, fieldErrors[] }` |
| **API envelope** | `ApiResponse<T>` / `PageResponse<T>` | `{ success, data, error }` consistently |
| **AuthN** | `JwtAuthFilter` before `UsernamePasswordAuthenticationFilter` | Bearer access token; `/api/auth/**` and `/actuator/health` are public |
| **AuthZ** | `@PreAuthorize` + ownership checks in services | A user only ever reads/writes their own (or their org's) rows |
| **Secrets** | Environment variables only | `.env` (git-ignored), `.env.example` documents every key |
| **CORS** | `SecurityConfig` | Allowed origin = `FRONTEND_ORIGIN` env var |
| **Observability** | Spring Actuator + structured logs | `/actuator/health`, `/actuator/info`; ML service `/health` |
| **Config** | `application.yml` + `application-docker.yml` profiles | No secrets committed |
| **Time / periods** | `common/util/PeriodUtil` | All reporting is monthly; a "period" is the first day of a month (UTC) |
| **Money-of-carbon** | `NUMERIC(14,3)` everywhere for quantities; `NUMERIC(6,2)` for scores/percent | Deterministic rounding at the edges only |

---

## 6. Security architecture (summary — full detail in API spec §Security)

- **Password hashing**: BCrypt (strength 12) via Spring Security `PasswordEncoder`.
- **JWT**: HS256 signed with `JWT_SECRET` (≥ 256-bit). Access token TTL 15 min,
  carries `sub` (user id), `email`, `role`. Refresh token TTL 7 days, opaque,
  **hashed at rest** in `refresh_tokens`, rotated on every use, revocable.
- **Roles**: `ADMIN`, `ORG_OWNER`, `MEMBER`, `VIEWER`. Reference-data writes
  (activity types, emission factors, benchmarks, scoring config) require `ADMIN`.
  Org-level aggregate views require `ORG_OWNER` or `ADMIN`.
- **Ownership enforcement**: every record/impact/score/prediction/anomaly query
  is filtered by the authenticated `userId` (or `organizationId` for org views)
  in the repository method signature — not just in a controller check.
- **Internal ML calls**: `Authorization: Bearer ${ML_SERVICE_TOKEN}` on a private
  network; the ML service rejects anything else.
- **CORS**: single configured origin, credentials disabled (token is sent
  explicitly, not via cookie).
- **Input hardening**: size-limited request bodies, `@Valid` everywhere,
  parameterized queries only (JPA), no string-built SQL.

---

## 7. Deployment topology

`docker-compose.yml` brings up five containers on one network:

| Service | Image / build | Port (host:container) | Depends on |
|---|---|---|---|
| `db` | `postgres:16-alpine` | `5432:5432` | — |
| `ml-service` | build `./ml-service` | `8000:8000` | `db` (none, actually) |
| `backend` | build `./backend` | `8080:8080` | `db`, `ml-service` |
| `frontend` | build `./frontend` (nginx serving static build) | `5173:80` | `backend` |
| `adminer` *(optional, dev only)* | `adminer` | `8081:8080` | `db` |

Healthchecks gate startup order. One command — `docker compose up --build` —
produces a fully working demo with seeded data.

---

## 8. Technology decisions & rationale

| Decision | Rationale | Alternative rejected |
|---|---|---|
| Spring Boot for the domain backend | Spec preference; mature security, JPA, validation, testing story | Node/Nest — weaker typed-domain + security conventions |
| Separate FastAPI ML tier | Python is where scikit-learn lives; keeps JVM lean; reproducible calls | Running ML in-JVM (DJL/Tribuo) — smaller ecosystem, harder to defend academically |
| PostgreSQL | Relational integrity for factors/records/impacts; `jsonb` for flexible attributes; window functions for trends | MongoDB — loses referential integrity that this domain needs |
| Flyway migrations | Deterministic, reviewable schema history; works in CI and Docker init | Hibernate `ddl-auto=update` — not production-safe, not reviewable |
| Configurable `emission_factors` table | Spec hard requirement; factors change yearly and by region | Hard-coded constants — not extensible, not defensible |
| LLM behind `LlmClient` interface | Spec hard requirement; provider outages / cost / offline demo | Direct SDK calls in services — vendor lock-in, untestable |
| `MockLlmClient` default in dev | Offline, deterministic demos and tests; no key needed to run the project | Requiring an API key to boot — bad DX for evaluators |
| Recharts | Declarative, small, themeable, covers every chart the dashboard needs | Chart.js — imperative; D3 — too low-level for the timeline |
| Monthly granularity | Matches how utility bills and emission factors are expressed | Daily — data most users can't supply; noisy for ML |

---

## 9. What "done" looks like (traceability to the brief)

| Brief section | Delivered by |
|---|---|
| A. Auth & users | `security/`, `auth/`, `user/`, `organization/` |
| B. Data entry | `activity/`, `record/`, frontend `/data` |
| 3. Calculation engine | `calculation/`, `emissionfactor/` (configurable) |
| 4. Environmental score | `scoring/` + `06-scoring-methodology.md` |
| 5. Dashboard | `metrics/` (`DashboardController`), frontend `/` |
| 6. Prediction | `prediction/` + ML `prediction/forecaster.py` |
| 7. Anomaly detection | `anomaly/` + ML `anomaly/detector.py` |
| 8. What-if | `scenario/` (`ScenarioEngine`) |
| 9. Recommendations | `recommendation/` (rules + optional LLM) |
| 10. AI assistant | `assistant/` + `integration/llm/` |
| 11. Benchmarking | `benchmark/` |
| 12. Explainability | `scoring/ScoreExplainer`, ML feature importance |
| 13. Reports + PDF | `report/` |
| 18. Security | `security/`, this doc §6 |
| 21. Sample data | `/database/seed/demo_data.sql` + generator script |
| 22. Testing | `backend/src/test`, `ml-service/tests`, frontend component tests |
| 23. Docker | `docker-compose.yml` + per-service `Dockerfile` |
| 24. README | `/README.md` |
| 25. BTech requirements | `07-btech-project-report.md` |

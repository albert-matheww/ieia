# IEIA — Development Plan

Incremental, module-by-module. Each stage ends with a runnable, testable
increment and a short status note (what was built, files touched, how to run,
how to test, what's left).

Legend: ⬜ not started · 🔨 in progress · ✅ done

---

## Stage 0 — Design foundation ✅

**Deliverables**
- `docs/01-architecture.md` — services, flows, cross-cutting concerns
- `docs/02-database-schema.md` + `database/migrations/V1__init_schema.sql`
- `database/migrations/V2__seed_reference_data.sql` — activity types, factors,
  scoring config, benchmarks
- `docs/03-api-specification.md` — every endpoint + DTOs + error model
- `docs/05-ml-methodology.md`, `docs/06-scoring-methodology.md`
- `docs/07-btech-project-report.md` (academic write-up, living document)
- `docker-compose.yml`, `.env.example`, `.gitignore`, `README.md`

**Run/verify:** `psql` the two migrations into a scratch DB; confirm all tables,
constraints, and seed rows exist.

---

## Stage 1 — Backend skeleton + Auth + Users/Orgs ✅

**Build**
- `backend/pom.xml` (Spring Boot 3.3, Java 17), `mvnw`, `Dockerfile`
- `application.yml` + `application-docker.yml`; Flyway wired to
  `src/main/resources/db/migration` (V1, V2 copied here as source of truth)
- `common/` — `ApiResponse`, `PageResponse`, `ErrorResponse`,
  `ApiException` hierarchy, `GlobalExceptionHandler`
- `security/` — `JwtService`, `JwtAuthFilter`, `SecurityConfig`,
  `PasswordEncoder` (BCrypt-12), `Role`, `@CurrentUser` argument resolver
- `auth/` — `AuthController`, `AuthService`, `RefreshToken` entity/repo,
  register/login/refresh/logout/me
- `user/`, `organization/` — entities, repos, `ProfileController`,
  `OrganizationController`

**Tests**
- `AuthControllerIT` (register → login → refresh → access protected → logout)
- `JwtServiceTest`, password-policy tests, unauthorized-access tests
- Testcontainers PostgreSQL

**Run:** `docker compose up db && ./mvnw -pl backend spring-boot:run`
**Verify:** `POST /api/auth/register`, `/login`, call `/api/auth/me` with token.

**Exit criteria:** JWT auth works end-to-end; RBAC enforced; migrations apply
cleanly on a fresh DB.

---

## Stage 2 — Reference data + Records + Calculation engine ✅

**Build**
- `activity/` — `ActivityType` entity/repo/service/controller (read for all,
  write for `ADMIN`)
- `emissionfactor/` — entity/repo/service/controller; `FactorResolver`
  (region + effective-date resolution, most-specific wins)
- `record/` — `EnvironmentalRecord` entity/repo/service/controller; DTO
  validation (amount > 0, unit ∈ allowedUnits, period not future, attributes vs
  `attribute_schema`)
- `calculation/` — `ImpactCalculationEngine`, `CalculatedImpact` entity/repo;
  writes one impact row per dimension; snapshots `factor_value`
- Recalculation orchestration: create/update/delete record → recompute impacts
  for that record → mark month dirty

**Tests**
- `FactorResolverTest` (region fallback, date windows, missing factor)
- `ImpactCalculationEngineTest` (kWh→CO2e & MJ; litre petrol→CO2e & MJ; waste
  routes; recycling → 0 landfilled mass)
- `RecordControllerIT` (CRUD, validation 422s, ownership 403)

**Exit criteria:** creating a record yields correct, sourced impact rows;
editing/deleting keeps impacts consistent.

---

## Stage 3 — Metrics aggregation + Scoring + Explainability ✅

**Build**
- `metrics/` — `MonthlyMetric` entity/repo; `MetricsAggregationService`
  (`recomputeMonth(user, period)` upserts totals, `co2e_by_category`,
  `co2e_by_activity`, `recycled_fraction`, `record_count`)
- `scoring/` — `ScoringConfig` entity/repo; `ScoringService` implementing
  `docs/06`; `EnvironmentalScore` entity/repo; `ScoreExplainer` (drivers)
- Wire the Stage 2 "month dirty" hook → aggregation → scoring, transactionally
- `POST /api/scores/recompute`, `GET /api/scores`, `/api/scores/{period}`,
  `/api/scores/methodology`

**Tests**
- `ScoringServiceTest` — worked examples from `docs/06` reproduced exactly
- `ScoreExplainerTest` — Δoverall equals Σ weighted category deltas
- `MetricsAggregationServiceTest` — category/activity maps, recycled fraction

**Exit criteria:** every `(user, month)` with data has a metric row and an
explainable score; recompute is idempotent.

---

## Stage 4 — Dashboard & Impact APIs ✅

**Build**
- `metrics/DashboardController` → `GET /api/dashboard`
- `ImpactController` → `/api/impact`, `/impact/trends`, `/impact/contributors`,
  `/impact/explain`
- Query layer: monthly series, deltas vs previous month, top-N contributors,
  waste composition, emissions-by-category/activity
- Filters: date range, category, activity type

**Tests**
- `DashboardControllerIT` — shape, deltas, empty/low-data states
- Query correctness against a seeded fixture user

**Exit criteria:** one call powers the whole dashboard; all numbers trace to
stored data.

---

## Stage 5 — ML service (Prediction + Anomaly + Analytics) ✅

**Build**
- `ml-service/` — FastAPI app, `Dockerfile`, `requirements.txt`,
  `core/config.py` (env), internal-token dependency
- `preprocessing/` — series builder, validation, feature engineering
- `prediction/forecaster.py` — model zoo + `TimeSeriesSplit` CV + interval +
  importance; `SeasonalNaive` fallback
- `anomaly/detector.py` — robust z-score, IQR, STL residual, Isolation Forest,
  consensus, severity, impact estimate
- `analytics/` — contributors, change decomposition
- `api/` — `/predict`, `/anomalies`, `/analytics/*`, `/health`

**Tests (`ml-service/tests`)**
- `test_preprocessing.py` — features, gap fill, min-history gate
- `test_forecaster.py` — CV runs, metrics sane, fallback triggers, determinism
  with fixed seed
- `test_detector.py` — precision/recall on injected anomalies (known truth)

**Run:** `uvicorn app.main:app --reload` or via compose.
**Exit criteria:** endpoints return the contracted shapes on fixture JSON; tests
green.

---

## Stage 6 — Backend ↔ ML integration (Predictions, Anomalies) ✅

**Build**
- `integration/ml/` — `MlServiceClient` interface + `RestMlServiceClient`
  (`RestClient`, timeouts, retry-once, `503` mapping), ML DTOs
- `prediction/` — `PredictionController`, `PredictionService`
  (series assembly, cache by series hash, persist `Prediction` + `model_run`)
- `anomaly/` — `AnomalyController`, `AnomalyService` (scan, persist, review
  workflow, dashboard feed)

**Tests**
- `PredictionServiceTest` with a stubbed `MlServiceClient`
- `AnomalyServiceIT` — scan seeded data, assert the injected spike is flagged
- ML-down path → `503 ML_SERVICE_UNAVAILABLE`, no fabricated data

**Exit criteria:** `/api/predictions` and `/api/anomalies` work against the live
ML service in compose.

---

## Stage 7 — What-If scenario engine ✅

**Build**
- `scenario/ScenarioEngine` — baseline from a period window; apply typed
  adjustments (`PCT_REDUCTION`, `PCT_INCREASE`, `MODAL_SHIFT`, `ABSOLUTE`,
  `RECYCLING_RATE`); recompute metrics + score with real factors; emit
  `assumptions[]`
- `scenario/` controller/service/entity/repo; `/simulate`, CRUD, `/compare`

**Tests**
- `ScenarioEngineTest` — 20% electricity cut → expected CO₂e delta (hand-checked);
  modal shift car→metro; recycling 40→70%
- Determinism (no randomness), idempotence

**Exit criteria:** scenarios match hand calculations; comparison endpoint returns
aligned bundles.

---

## Stage 8 — Recommendation engine + Benchmarking ✅

**Build**
- `recommendation/RecommendationEngine` — rank contributors → parameterised
  actions → cost via `ScenarioEngine` → ranked `Recommendation` rows;
  `GENERIC` fallback when data is thin; optional LLM re-phrasing
- `recommendation/` controller/service/repo; generate + status endpoints
- `benchmark/` — entity/repo/service/controller; `BenchmarkingService`
  (period-over-period, year-over-year, category ratings vs configurable bands)

**Tests**
- `RecommendationEngineTest` — top contributor drives rank 1; numbers equal
  scenario-engine output; `basis` flag correct
- `BenchmarkingServiceTest` — rating band boundaries, YoY when ≥ 13 months

**Exit criteria:** recommendations are data-driven and quantified; benchmark
comparison matches spec shape with the disclaimer.

---

## Stage 9 — AI Assistant + LLM abstraction ✅

**Build**
- `integration/llm/` — `LlmClient` + `OpenAiLlmClient`, `AnthropicLlmClient`,
  `OllamaLlmClient`, `MockLlmClient`, `LlmClientFactory`, `PromptBuilder`
- `assistant/` — `ConversationContextBuilder` (real analytics bundle),
  `AssistantService` (prompt, call, numeric post-check, persist),
  `AiAssistantController`; `ai_conversations` / `ai_messages`
- Grounding guarantees + `disclaimers[]`

**Tests**
- `AssistantServiceTest` with `MockLlmClient` — context contains real figures;
  fabricated-number guard triggers; "insufficient data" path
- `PromptBuilderTest` — system prompt invariants

**Exit criteria:** assistant answers the rubric's sample questions using only
real context; runs with **no API key** via `MockLlmClient`.

---

## Stage 10 — Reports + PDF export ✅

**Build**
- `report/` — `ReportPayloadBuilder` (assembles summary, scores, trends,
  contributors, anomalies, predictions, recommendations, scenarios, benchmarks,
  methodology), `PdfRenderer` (OpenPDF/Flying Saucer HTML→PDF), controller,
  entity/repo, `/download`
- Volume mount for generated PDFs

**Tests**
- `ReportPayloadBuilderTest` — sections present, numbers match source APIs
- `ReportControllerIT` — JSON sync path, PDF async `GENERATING→READY`, download
  headers

**Exit criteria:** a full PDF report generates for the demo user.

---

## Stage 11 — Frontend ✅

**Build (Vite + React 18 + TS + Tailwind + TanStack Query + Recharts)**
- `api/` axios instance (auth interceptor, 401→refresh→retry), typed clients
- `context/AuthContext`, `<AuthGuard>`, `<RoleGuard>`
- `components/layout` (sidebar, topbar, page shell), `components/ui`
  (Card, Stat, Table, Button, Input, Select, Modal, Toast, EmptyState,
  Skeleton, ErrorState), `components/charts` (Line, Area, Bar, Pie wrappers)
- Pages: Login, Register, Dashboard, Environmental Data, Impact Analysis,
  Predictions, Anomaly Detection, What-If Simulator, AI Recommendations,
  AI Assistant, Reports, Settings/Profile
- Loading / empty / error states everywhere; responsive; light+dark

**Tests**
- Vitest + Testing Library: `StatCard`, `RecordForm` validation,
  `ScenarioBuilder` math display, `AuthGuard` redirect, an API-client test with
  MSW

**Exit criteria:** full click-through of all 12 pages against the live backend
with seeded data.

---

## Stage 12 — Seed/demo data generator ✅

**Build**
- `database/seed/demo_data.sql` **and** `scripts/generate_demo_data.py` (or a
  backend `CommandLineRunner` behind `--seed-demo`)
- One org + `ORG_OWNER`, two individual demo users, `ADMIN`
- 18–24 months of monthly records per user across all categories with:
  - a rising electricity trend + a summer seasonal bump
  - one injected electricity **spike** (~+37%) and one water **drop**
  - a gradual recycling-rate improvement
  - a transport step-change (new commute)
- Enough history for CV, visible trends, real anomalies, meaningful
  recommendations, and YoY benchmarking

**Exit criteria:** `docker compose up` → log in as the demo user → every feature
has non-trivial content.

---

## Stage 13 — Dockerisation + orchestration polish ✅

- Per-service `Dockerfile` (multi-stage: Maven build, `nginx` static frontend,
  slim Python)
- `docker-compose.yml` healthchecks + depends_on ordering; one-command boot with
  auto-migrate and auto-seed (dev profile)
- `Makefile` / `scripts/*.sh` convenience targets

**Exit criteria:** fresh clone → `cp .env.example .env` → `docker compose up
--build` → working app with data.

---

## Stage 14 — Testing pass + docs finalisation ✅

- Cross-cutting tests from the brief present: auth flows + 401/403/409/400,
  invalid input (422), unauthorized/ownership, record CRUD, scenario
  hand-calculations, anomaly precision/recall on injected ground truth.
  → 41 backend unit + 10 Testcontainers IT classes, 21 ML, 11 frontend.
- `docs/05 §6` and `docs/07 §7` populated from a real `IEIA_SEED_DEMO` run
  (model_runs metrics, anomaly P/R/F1, worked-example cross-checks).
- `README.md` finalised — one-command Docker, `scripts/dev.sh` for no-Docker,
  env var table, demo logins, API docs link, testing matrix, limitations,
  future scope, honest "verified / not verified here" note.
- `.github/workflows/ci.yml` — backend `mvn verify`, ML `pytest`, frontend
  typecheck + test + build, `docker compose config` validation.

**Exit criteria met:** README lets a stranger run and understand the project;
runnable test suites green; report defensible with real numbers.

---

## Cross-stage definition of done — all satisfied

- ✅ No hard-coded dashboard numbers — every value derives from stored data
  (verified: fresh user → 404 empty state; all figures trace to `monthly_metrics` /
  `environmental_scores` / `calculated_impacts`).
- ✅ No random/fake ML outputs — models trained on the user's real monthly series;
  `SeasonalNaive` fallback is labelled; ML outage → `503`, never a fabricated
  forecast; assistant fabrication guard tested.
- ✅ Secrets only via env vars; `.env` git-ignored; `.env.example` complete.
- ✅ Each domain: Controller → Service → Repository; DTOs at the boundary; no
  entities leaked to the web layer (one deliberate exception: `GET /api/benchmarks`
  returns the config rows, noted).
- ✅ Every endpoint is in `docs/03-api-specification.md`.

---

## Post-completion run (2026-09-08)

Full stack brought up (isolated Postgres + ML + backend + Vite), demo account
`alex@ieia.demo` seeded through the real pipeline, every page driven in a browser.
Two defects found and fixed under the "no superficial prototype" bar:

- **Async report stuck PENDING.** `ReportService.create()` dispatched the
  `@Async` PDF job before its transaction committed; the worker read the row back
  by id and saw nothing. Fixed with an `afterCommit` transaction-synchronisation
  hook. New `ReportAsyncIT` exercises `ieia.reports.async=true` end to end.
- **PDF was a `toString()` dump.** `ReportPayloadBuilder` passed typed DTO records
  to the renderer's Map/List walker. Reshaped every section to flat
  `List<Map>` / `Map` of scalars; `PdfRenderer` switched to A4 landscape. Output
  is now a clean ~8-page report (summary, 24-month scores + trends, contributors,
  anomalies, predictions with honest R²/MAE, ranked recommendations, benchmarks
  with the non-official disclaimer, methodology).

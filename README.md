# Intelligent Environmental Impact Analyzer (IEIA)

> An AI-powered environmental intelligence platform that transforms environmental
> activity data into measurable impact assessments, predictive insights, anomaly
> detection, scenario analysis, and actionable sustainability recommendations.

IEIA is a full-stack analytics platform — not a carbon calculator. You log
environmental activity (electricity, fuel, transport, water, waste, materials);
IEIA computes CO₂e / energy / water / waste impact and a transparent 0–100
environmental score, then uses ML and an LLM to **forecast**, **flag anomalies**,
**simulate what-if changes**, **recommend prioritised actions**, **benchmark**,
and **answer questions** — always grounded in your real stored data.

---

## Status — all stages complete

Built incrementally per [`docs/04-development-plan.md`](docs/04-development-plan.md).

| Stage | Scope | State |
|---|---|---|
| 0 | Architecture, DB schema, API spec, dev plan, infra scaffolding | ✅ |
| 1 | Backend skeleton + Auth + Users/Orgs (JWT, refresh rotation, RBAC) | ✅ |
| 2 | Reference data + Records + Calculation engine (configurable factors, unit conversion) | ✅ |
| 3 | Metrics aggregation + Scoring + Explainability (transparent 0-100 score, drivers) | ✅ |
| 4 | Dashboard & Impact APIs (one-call payload, trends, contributors, explain) | ✅ |
| 5 | ML service (model zoo + TS-CV, anomaly ensemble, analytics) | ✅ |
| 6 | Backend ↔ ML integration (predictions cached, anomaly review workflow) | ✅ |
| 7 | What-if scenario engine (typed levers, hand-checked, deterministic) | ✅ |
| 8 | Recommendations (priced via scenario engine) + Benchmarking + LLM abstraction | ✅ |
| 9 | AI Assistant (grounded context bundle, numeric fabrication guard) | ✅ |
| 10 | Reports (10 sections from every engine) + async PDF via OpenPDF | ✅ |
| 11 | Frontend — 12 pages, "monitoring station" design, gauge instrument, dark/light | ✅ |
| 12 | Seed/demo generator — 3 accounts, 24 months, injected spike + drop, full pipeline | ✅ |
| 13 | Dockerisation — 5-service compose (config-validated), Makefile, dev.sh | ✅ |
| 14 | Testing pass + docs finalisation (results filled from real runs) | ✅ |

**Verified on the build machine:** backend boots against PostgreSQL and applies
Flyway V1+V2; every REST flow exercised end-to-end via curl; 41 backend unit
tests, 21 ML tests, 11 frontend tests green; frontend built and driven in a
browser against the live backend (auth → dashboard → predictions → assistant).
**Not verified here:** `docker compose up` (no Docker daemon on this machine —
compose config validates, Dockerfiles are standard multi-stage) and the 9
Testcontainers integration test classes (need Docker; they compile and run in CI).

### Demo logins (with `IEIA_SEED_DEMO=true`, the default)

| Email | Role | Data |
|---|---|---|
| `alex@ieia.demo` | MEMBER (India) | 24 months, rising electricity + summer bump, injected water drop, commute switch |
| `owner@greenfield.demo` | ORG_OWNER | 24 months, per-employee org footprint |
| `admin@ieia.demo` | ADMIN | reference-data maintenance |

Password for all three: `Demo!Pass1`

---

## Features

- **Auth & users** — registration, login, JWT access + rotating refresh tokens,
  BCrypt hashing, roles (`ADMIN`, `ORG_OWNER`, `MEMBER`, `VIEWER`), profile,
  organization info.
- **Environmental data entry** — validated CRUD across electricity, fuel,
  transport, water, waste, materials with configurable units and
  category-specific attributes.
- **Calculation engine** — activity → impact via a **configurable, versioned,
  region-aware `emission_factors` table**. No factors hard-coded in logic. Raw
  data, calculated emissions, and aggregated metrics are separate tiers.
- **Environmental score (0–100)** — five category sub-scores (carbon, energy,
  water, waste, transport), weighted, **hand-recomputable** from published
  thresholds. Month-over-month changes decomposed into drivers.
- **Dashboard** — summary cards with period deltas; trend, composition, and
  contributor charts; filters by date range / category / activity.
- **ML — prediction** — LinearRegression / RandomForest / GradientBoosting zoo
  with time-series cross-validation, MAE/RMSE/R², prediction intervals, feature
  importance, and a `SeasonalNaive` fallback for short history.
- **ML — anomaly detection** — robust z-score + IQR + STL residual + Isolation
  Forest ensemble with consensus voting, severity, and estimated CO₂e impact.
- **What-if simulator** — deterministic levers (percentage change, modal shift,
  recycling rate, absolute) over your real baseline and real factors; compare
  scenarios side by side.
- **AI recommendations** — rule engine ranks *your* dominant contributors and
  prices each action with the scenario engine; the LLM may only re-phrase.
- **AI assistant** — natural-language Q&A grounded in a real analytics context
  bundle; **never invents numbers**, admits missing data.
- **Benchmarking** — period-over-period, year-over-year, and configurable
  reference bands (clearly labelled non-official).
- **Explainability** — "your score changed by X because…"; ML feature importance
  surfaced in the UI.
- **Reports** — full environmental report (period, scores, metrics, contributors,
  trends, anomalies, predictions, recommendations, scenarios) exportable as PDF.

---

## Architecture

```
React SPA  ──HTTPS/JSON──▶  Spring Boot API  ──JDBC──▶  PostgreSQL
                                  │
                    ┌─────────────┼──────────────┐
                    ▼             ▼              ▼
              Calculation    ML Service     LLM Provider
                Engine        (FastAPI)     (Mock/OpenAI/
              + Scoring     predict/anomaly  Anthropic/Ollama)
              + Scenario     /analytics       via LlmClient
```

- **Frontend** — React 18 + Vite + TypeScript, TanStack Query, Recharts, Tailwind.
- **Backend** — Spring Boot 3, Spring Security + JWT, Spring Data JPA, Flyway.
  Controller → Service → Repository; DTOs at the boundary.
- **ML service** — FastAPI + pandas + NumPy + scikit-learn + statsmodels.
  **Stateless** — receives series in the request, no DB access.
- **Database** — PostgreSQL 16, normalized, Flyway-migrated.
- **AI** — `LlmClient` interface; provider chosen by `IEIA_LLM_PROVIDER`;
  `MockLlmClient` is the default so the project runs with **no API key**.

Full detail: [`docs/01-architecture.md`](docs/01-architecture.md).

---

## Tech stack

| Layer | Technology |
|---|---|
| Frontend | React 18, Vite, TypeScript, React Router, TanStack Query, Recharts, Tailwind CSS, React Hook Form + Zod, Axios |
| Backend | Java 17, Spring Boot 3.3, Spring Security, JJWT, Spring Data JPA, Hibernate, Flyway, Bean Validation, springdoc-openapi, OpenPDF |
| ML service | Python 3.11+, FastAPI, Uvicorn, pandas, NumPy, scikit-learn, statsmodels, pydantic-settings |
| Database | PostgreSQL 16 |
| Infra | Docker, Docker Compose, nginx (static frontend) |
| Testing | JUnit 5, Mockito, Spring Test, Testcontainers, REST-assured; pytest; Vitest + Testing Library + MSW |

---

## Prerequisites

- **Docker** + **Docker Compose v2** (only hard requirement for the full stack)

For local (non-Docker) development:
- **JDK 17** (`sdk use java 17.0.16-tem`)
- **Node 20+** and npm
- **Python 3.11+**
- **PostgreSQL 16** (or just run the `db` compose service)

---

## Installation & running

### Option A — Docker (recommended)

```bash
git clone <repo-url> IEIA && cd IEIA
cp .env.example .env          # then edit secrets (JWT_SECRET, DB password, ...)
docker compose up --build     # add:  --profile dev   to also start Adminer
```

| Service | URL |
|---|---|
| Frontend (SPA) | http://localhost:5173 |
| Backend API | http://localhost:8080/api |
| API docs (Swagger UI) | http://localhost:8080/swagger-ui.html |
| ML service | http://localhost:8000/docs |
| Adminer (`--profile dev`) | http://localhost:8081 |

On first boot the backend runs Flyway (`V1` schema, `V2` reference data) and,
with `IEIA_SEED_DEMO=true`, loads the demo dataset. Log in with the demo
credentials printed in the backend logs (also listed in
`database/seed/demo_data.sql`).

### Option B — Local development (no Docker)

One script starts an isolated PostgreSQL cluster, the ML service, the backend
(with demo data) and the Vite dev server, and tears them all down on Ctrl-C:

```bash
cd ml-service && python3.12 -m venv .venv && .venv/bin/pip install -r requirements.txt && cd ..
cd frontend && npm install && cd ..
./scripts/dev.sh
```

Or run each service by hand:

```bash
# 1. Database — an isolated cluster (leaves your own PostgreSQL alone)
initdb -D /tmp/ieia-pg -U ieia --auth=trust --locale=C
pg_ctl -D /tmp/ieia-pg -o "-p 55433 -k /tmp" -l /tmp/ieia-pg.log -w start
PGHOST=127.0.0.1 PGPORT=55433 createdb -U ieia ieia

# 2. ML service
cd ml-service && .venv/bin/uvicorn app.main:app --reload --port 8000

# 3. Backend (JDK 17 required — e.g. `sdk use java 17.0.16-tem`)
cd backend && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:55433/ieia \
  SPRING_DATASOURCE_USERNAME=ieia SPRING_DATASOURCE_PASSWORD=ieia \
  JWT_SECRET=dev-secret-dev-secret-dev-secret-0123456789 \
  ML_SERVICE_URL=http://127.0.0.1:8000 ./mvnw spring-boot:run

# 4. Frontend
cd frontend && npm run dev          # http://localhost:5173, proxies /api -> :8080
```

> PostgreSQL 18 on macOS needs `LC_ALL=C` set when starting the cluster
> (a known `postmaster became multithreaded` issue); `scripts/dev.sh` handles it.

---

## Environment variables

All configuration is via environment variables — see
[`.env.example`](.env.example) for the complete, commented list. Highlights:

| Variable | Purpose | Default |
|---|---|---|
| `POSTGRES_*`, `SPRING_DATASOURCE_URL` | Database connection | — |
| `JWT_SECRET` | JWT signing key (≥ 256-bit) | — (**must set**) |
| `JWT_ACCESS_TTL_SECONDS` / `JWT_REFRESH_TTL_SECONDS` | Token lifetimes | 900 / 604800 |
| `FRONTEND_ORIGIN` | CORS allowed origin | http://localhost:5173 |
| `ML_SERVICE_URL` / `ML_SERVICE_TOKEN` | Backend → ML tier | http://ml-service:8000 |
| `IEIA_LLM_PROVIDER` | `MOCK` \| `OPENAI` \| `ANTHROPIC` \| `OLLAMA` | `MOCK` |
| `OPENAI_API_KEY` / `ANTHROPIC_API_KEY` | Only when that provider is selected | — |
| `IEIA_SEED_DEMO` | Load demo data on startup | `true` |
| `VITE_API_BASE_URL` | API base baked into the SPA build | http://localhost:8080/api |

No secret is committed; `.env` is git-ignored.

---

## Database setup

- Schema: `database/migrations/V1__init_schema.sql`
- Reference data: `database/migrations/V2__seed_reference_data.sql`
- Demo data: `database/seed/demo_data.sql`

These are applied automatically by Flyway from
`backend/src/main/resources/db/migration` (the canonical copy). To apply
manually against a running `db`:

```bash
psql "postgresql://ieia:$POSTGRES_PASSWORD@localhost:5432/ieia" \
  -f database/migrations/V1__init_schema.sql \
  -f database/migrations/V2__seed_reference_data.sql \
  -f database/seed/demo_data.sql
```

Schema design and ERD: [`docs/02-database-schema.md`](docs/02-database-schema.md).

---

## API documentation

- Human spec: [`docs/03-api-specification.md`](docs/03-api-specification.md)
- Live OpenAPI: `http://localhost:8080/v3/api-docs` · Swagger UI:
  `http://localhost:8080/swagger-ui.html`

All responses use the envelope `{ success, data, error }`. Auth is
`Authorization: Bearer <accessToken>`.

---

## ML methodology

See [`docs/05-ml-methodology.md`](docs/05-ml-methodology.md). Summary:
interpretable model zoo, expanding-window time-series cross-validation, reported
MAE/RMSE/R², explicit uncertainty intervals, `SeasonalNaive` fallback for short
history; anomaly detection is a robust-statistics + Isolation-Forest ensemble
evaluated against injected ground-truth anomalies. No fabricated results — every
model trains on the user's real series.

## AI integration

The LLM sits behind `integration/llm/LlmClient`. Providers: `MockLlmClient`
(default, deterministic, no key), `OpenAiLlmClient`, `AnthropicLlmClient`,
`OllamaLlmClient`. The assistant receives a JSON context bundle of the user's
real analytics and a system prompt forbidding invented numbers; replies are
numerically post-checked against the bundle and shipped with `disclaimers[]`.

## Scoring methodology

See [`docs/06-scoring-methodology.md`](docs/06-scoring-methodology.md) — a
transparent, configurable, hand-recomputable 0–100 formula. The score is **not**
an ML output.

---

## Testing

```bash
make test              # all three suites
make test-backend-unit # 41 backend unit tests, no Docker needed
make test-backend      # + 10 Testcontainers integration classes (needs Docker)
make test-ml           # 21 pytest tests
make test-frontend     # 11 vitest tests
```

| Suite | Count | Covers |
|---|---:|---|
| Backend unit | 41 | JWT codec, factor resolution, unit conversion, impact engine, scoring worked examples (docs/06 reproduced exactly), driver decomposition, mock LLM grounding |
| Backend IT (Testcontainers PostgreSQL) | 10 classes | auth flows + 401/403/409/400, record CRUD + 422s + ownership, record→metric→score pipeline, dashboard shape + deltas + empty state, ML integration (cache, 503 on outage), scenario hand-checks, recommendations ranked/priced, assistant fabrication guard, report sections + PDF download, async report reaches READY + downloads |
| ML service | 21 | preprocessing + feature matrix, model-zoo CV + SeasonalNaive fallback + determinism, anomaly precision/recall on injected truth + zero false positives, HTTP contract |
| Frontend | 11 | format helpers, StatCard / DeltaTag / LevelMeter / ScoreGauge |

Real evaluation numbers are in [`docs/07-btech-project-report.md §7`](docs/07-btech-project-report.md)
and [`docs/05-ml-methodology.md §6`](docs/05-ml-methodology.md).

---

## Project structure

```
IEIA/
├── docs/                      Architecture, schema, API, ML & scoring methodology, report
├── database/
│   ├── migrations/            V1 schema, V2 reference data (Flyway)
│   └── seed/                  demo_data.sql
├── backend/                   Spring Boot API (com.ieia.*)
│   └── src/main/java/com/ieia/{security,auth,user,organization,activity,
│       emissionfactor,record,calculation,scoring,metrics,benchmark,prediction,
│       anomaly,scenario,recommendation,assistant,report,integration/{ml,llm},common,config}
├── ml-service/                FastAPI (app/{preprocessing,prediction,anomaly,analytics,api})
├── frontend/                  React SPA (src/{api,components,pages,hooks,context,utils,types})
├── scripts/                   demo data generator, helper scripts
├── docker-compose.yml
├── .env.example
└── README.md
```

---

## Limitations

Emission factors are illustrative public-dataset values, not exhaustively
region/year-matched. Monthly granularity means ML trains on tens of points —
R² can be modest and intervals wide (always surfaced). No weather/price/occupancy
covariates in v1. Scoring thresholds are a defensible choice, not an official
standard. The AI assistant will not invent numbers but is not a certified
advisor. Full discussion: [`docs/07-btech-project-report.md`](docs/07-btech-project-report.md) §8.

## Future improvements

IoT / smart-meter ingestion, real-time sensor alerting, Scope 3 / supply-chain
analysis, advanced forecasting with exogenous regressors, a React Native mobile
app, organization-wide target tracking, and per-country/year verified
emission-factor packs. See `docs/07` §9.

---

## License

Academic project — add a license of your choice before publishing.

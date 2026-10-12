-- =============================================================================
-- IEIA — Intelligent Environmental Impact Analyzer
-- V1: Initial schema
-- PostgreSQL 16
-- =============================================================================
-- Conventions:
--   * UUID primary keys via gen_random_uuid()
--   * TIMESTAMPTZ everywhere; updated_at maintained by set_updated_at() trigger
--   * Enumerations as VARCHAR + CHECK (maps to JPA @Enumerated(STRING))
--   * Quantities/impacts NUMERIC(16,4); scores/percentages NUMERIC(6,2)
--   * A "period" is a DATE pinned to the first day of a month
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid()

-- -----------------------------------------------------------------------------
-- Shared helper: keep updated_at current
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Shared helper: enforce first-of-month periods
CREATE OR REPLACE FUNCTION enforce_first_of_month()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.period_month <> date_trunc('month', NEW.period_month::timestamp)::date THEN
        RAISE EXCEPTION 'period_month must be the first day of a month, got %', NEW.period_month;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =============================================================================
-- ORGANIZATIONS
-- =============================================================================
CREATE TABLE organizations (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(160) NOT NULL,
    industry       VARCHAR(120),
    country        VARCHAR(80),
    region         VARCHAR(80)  NOT NULL DEFAULT 'GLOBAL',
    size_category  VARCHAR(20)  NOT NULL DEFAULT 'INDIVIDUAL'
                   CHECK (size_category IN ('INDIVIDUAL','SMALL','MEDIUM','LARGE')),
    headcount      INTEGER CHECK (headcount IS NULL OR headcount > 0),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TRIGGER trg_organizations_updated
    BEFORE UPDATE ON organizations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================================
-- USERS
-- =============================================================================
CREATE TABLE users (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email            VARCHAR(255) NOT NULL,
    password_hash    VARCHAR(100) NOT NULL,
    full_name        VARCHAR(160) NOT NULL,
    role             VARCHAR(20)  NOT NULL DEFAULT 'MEMBER'
                     CHECK (role IN ('ADMIN','ORG_OWNER','MEMBER','VIEWER')),
    organization_id  UUID REFERENCES organizations(id) ON DELETE RESTRICT,
    country          VARCHAR(80),
    -- Region drives emission-factor resolution and scoring baselines for
    -- individual users (organisation users inherit their org's region).
    region           VARCHAR(80)  NOT NULL DEFAULT 'GLOBAL',
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at    TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_users_email ON users (lower(email));
CREATE INDEX ix_users_org ON users (organization_id);
CREATE TRIGGER trg_users_updated
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================================
-- REFRESH TOKENS  (opaque, hashed at rest, rotated on use)
-- =============================================================================
CREATE TABLE refresh_tokens (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash   VARCHAR(64) NOT NULL,                 -- SHA-256 hex
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked      BOOLEAN NOT NULL DEFAULT FALSE,
    user_agent   VARCHAR(255),
    ip_address   VARCHAR(64),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_refresh_tokens_hash ON refresh_tokens (token_hash);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_expiry ON refresh_tokens (expires_at);

-- =============================================================================
-- ACTIVITY TYPES  (reference data — the catalogue of loggable activities)
-- =============================================================================
CREATE TABLE activity_types (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code              VARCHAR(64) NOT NULL,
    category          VARCHAR(20) NOT NULL
                      CHECK (category IN ('ELECTRICITY','TRANSPORT','FUEL','WATER','WASTE','MATERIALS')),
    name              VARCHAR(160) NOT NULL,
    description       TEXT,
    default_unit      VARCHAR(24) NOT NULL,
    allowed_units     JSONB NOT NULL DEFAULT '[]'::jsonb,   -- ["kWh","MWh"]
    attribute_schema  JSONB NOT NULL DEFAULT '{}'::jsonb,   -- dynamic-form hints
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_activity_types_code ON activity_types (code);
CREATE INDEX ix_activity_types_category ON activity_types (category);
CREATE TRIGGER trg_activity_types_updated
    BEFORE UPDATE ON activity_types
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================================
-- EMISSION FACTORS  (reference data — configurable conversion factors)
--   impact = amount_in(unit_from) * value   ->  unit_to
-- =============================================================================
CREATE TABLE emission_factors (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_type_id  UUID NOT NULL REFERENCES activity_types(id) ON DELETE RESTRICT,
    impact_type       VARCHAR(12) NOT NULL
                      CHECK (impact_type IN ('CO2E','ENERGY','WATER','WASTE')),
    unit_from         VARCHAR(24) NOT NULL,
    unit_to           VARCHAR(24) NOT NULL
                      CHECK (unit_to IN ('kgCO2e','MJ','litre','kg')),
    value             NUMERIC(16,6) NOT NULL CHECK (value >= 0),
    region            VARCHAR(80) NOT NULL DEFAULT 'GLOBAL',
    source            VARCHAR(255) NOT NULL,
    effective_from    DATE NOT NULL DEFAULT DATE '2000-01-01',
    effective_to      DATE,
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_ef_window CHECK (effective_to IS NULL OR effective_to > effective_from)
);
CREATE UNIQUE INDEX ux_emission_factors_slice
    ON emission_factors (activity_type_id, impact_type, region, unit_from, effective_from);
CREATE INDEX ix_emission_factors_resolve
    ON emission_factors (activity_type_id, impact_type, region, effective_from DESC);
CREATE TRIGGER trg_emission_factors_updated
    BEFORE UPDATE ON emission_factors
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================================
-- ENVIRONMENTAL RECORDS  (raw activity data — exactly what the user entered)
-- =============================================================================
CREATE TABLE environmental_records (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_id   UUID REFERENCES organizations(id) ON DELETE SET NULL,
    activity_type_id  UUID NOT NULL REFERENCES activity_types(id) ON DELETE RESTRICT,
    period_month      DATE NOT NULL,
    amount            NUMERIC(16,4) NOT NULL CHECK (amount > 0),
    unit              VARCHAR(24) NOT NULL,
    location          VARCHAR(160),
    source_detail     VARCHAR(120),
    attributes        JSONB NOT NULL DEFAULT '{}'::jsonb,
    note              TEXT,
    created_by        UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_records_user_period      ON environmental_records (user_id, period_month);
CREATE INDEX ix_records_user_act_period  ON environmental_records (user_id, activity_type_id, period_month);
CREATE INDEX ix_records_org_period       ON environmental_records (organization_id, period_month);
CREATE INDEX ix_records_activity         ON environmental_records (activity_type_id);
CREATE TRIGGER trg_records_updated
    BEFORE UPDATE ON environmental_records
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_records_first_of_month
    BEFORE INSERT OR UPDATE ON environmental_records
    FOR EACH ROW EXECUTE FUNCTION enforce_first_of_month();

-- =============================================================================
-- CALCULATED IMPACTS  (derived — one row per record per impact dimension)
-- =============================================================================
CREATE TABLE calculated_impacts (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    environmental_record_id  UUID NOT NULL REFERENCES environmental_records(id) ON DELETE CASCADE,
    user_id                  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    period_month             DATE NOT NULL,
    impact_type              VARCHAR(12) NOT NULL
                             CHECK (impact_type IN ('CO2E','ENERGY','WATER','WASTE')),
    value                    NUMERIC(16,4) NOT NULL,
    unit                     VARCHAR(24) NOT NULL,
    emission_factor_id       UUID REFERENCES emission_factors(id) ON DELETE SET NULL,
    factor_value             NUMERIC(16,6),                 -- snapshot at calc time
    calculation_method       VARCHAR(120) NOT NULL DEFAULT 'amount * factor',
    calculated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_impacts_record_type ON calculated_impacts (environmental_record_id, impact_type);
CREATE INDEX ix_impacts_user_period_type   ON calculated_impacts (user_id, period_month, impact_type);
CREATE TRIGGER trg_impacts_first_of_month
    BEFORE INSERT OR UPDATE ON calculated_impacts
    FOR EACH ROW EXECUTE FUNCTION enforce_first_of_month();

-- =============================================================================
-- MONTHLY METRICS  (aggregated rollup per user per month)
-- =============================================================================
CREATE TABLE monthly_metrics (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_id     UUID REFERENCES organizations(id) ON DELETE SET NULL,
    period_month        DATE NOT NULL,
    total_co2e_kg       NUMERIC(16,4) NOT NULL DEFAULT 0,
    total_energy_mj     NUMERIC(16,4) NOT NULL DEFAULT 0,
    total_water_litre   NUMERIC(16,4) NOT NULL DEFAULT 0,
    total_waste_kg      NUMERIC(16,4) NOT NULL DEFAULT 0,
    recycled_fraction   NUMERIC(6,4)  NOT NULL DEFAULT 0 CHECK (recycled_fraction BETWEEN 0 AND 1),
    co2e_by_category    JSONB NOT NULL DEFAULT '{}'::jsonb,
    co2e_by_activity    JSONB NOT NULL DEFAULT '{}'::jsonb,
    record_count        INTEGER NOT NULL DEFAULT 0,
    computed_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_monthly_metrics_user_period ON monthly_metrics (user_id, period_month);
CREATE INDEX ix_monthly_metrics_org_period         ON monthly_metrics (organization_id, period_month);
CREATE TRIGGER trg_monthly_metrics_first_of_month
    BEFORE INSERT OR UPDATE ON monthly_metrics
    FOR EACH ROW EXECUTE FUNCTION enforce_first_of_month();

-- =============================================================================
-- SCORING CONFIG  (configurable, versioned scoring methodology)
-- =============================================================================
CREATE TABLE scoring_configs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version       VARCHAR(20) NOT NULL,
    is_active     BOOLEAN NOT NULL DEFAULT FALSE,
    weights       JSONB NOT NULL,   -- {"carbon":0.35,"energy":0.20,"water":0.15,"waste":0.15,"transport":0.15}
    baselines     JSONB NOT NULL,   -- good/poor thresholds per metric per scope/region
    scale_params  JSONB NOT NULL DEFAULT '{}'::jsonb,
    description   TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_scoring_configs_version ON scoring_configs (version);
CREATE UNIQUE INDEX ux_scoring_configs_active  ON scoring_configs (is_active) WHERE is_active;
CREATE TRIGGER trg_scoring_configs_updated
    BEFORE UPDATE ON scoring_configs
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================================
-- ENVIRONMENTAL SCORES  (aggregated — the transparent 0..100 score)
-- =============================================================================
CREATE TABLE environmental_scores (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_id    UUID REFERENCES organizations(id) ON DELETE SET NULL,
    period_month       DATE NOT NULL,
    scoring_config_id  UUID NOT NULL REFERENCES scoring_configs(id) ON DELETE RESTRICT,
    overall_score      NUMERIC(6,2) NOT NULL CHECK (overall_score BETWEEN 0 AND 100),
    carbon_score       NUMERIC(6,2) NOT NULL CHECK (carbon_score BETWEEN 0 AND 100),
    energy_score       NUMERIC(6,2) NOT NULL CHECK (energy_score BETWEEN 0 AND 100),
    water_score        NUMERIC(6,2) NOT NULL CHECK (water_score BETWEEN 0 AND 100),
    waste_score        NUMERIC(6,2) NOT NULL CHECK (waste_score BETWEEN 0 AND 100),
    transport_score    NUMERIC(6,2) NOT NULL CHECK (transport_score BETWEEN 0 AND 100),
    score_breakdown    JSONB NOT NULL DEFAULT '{}'::jsonb,
    previous_score     NUMERIC(6,2),
    delta              NUMERIC(6,2),
    drivers            JSONB NOT NULL DEFAULT '[]'::jsonb,
    computed_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_scores_user_period ON environmental_scores (user_id, period_month);
CREATE INDEX ix_scores_org_period         ON environmental_scores (organization_id, period_month);
CREATE TRIGGER trg_scores_first_of_month
    BEFORE INSERT OR UPDATE ON environmental_scores
    FOR EACH ROW EXECUTE FUNCTION enforce_first_of_month();

-- =============================================================================
-- BENCHMARKS  (configurable comparison values — clearly labelled, not official)
-- =============================================================================
CREATE TABLE benchmarks (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scope          VARCHAR(20) NOT NULL CHECK (scope IN ('INDIVIDUAL','ORGANIZATION')),
    segment        VARCHAR(80) NOT NULL,       -- e.g. IN_URBAN_HOUSEHOLD, OFFICE_PER_EMPLOYEE
    metric         VARCHAR(12) NOT NULL CHECK (metric IN ('CO2E','ENERGY','WATER','WASTE','SCORE')),
    unit           VARCHAR(24) NOT NULL,
    period         VARCHAR(8)  NOT NULL CHECK (period IN ('MONTH','YEAR')),
    value          NUMERIC(16,4) NOT NULL,
    rating_bands   JSONB NOT NULL DEFAULT '{}'::jsonb,   -- {"good":[0,x],"average":[x,y],"needs_improvement":[y,null]}
    region         VARCHAR(80) NOT NULL DEFAULT 'GLOBAL',
    source         VARCHAR(255) NOT NULL,
    is_official    BOOLEAN NOT NULL DEFAULT FALSE,
    effective_from DATE NOT NULL DEFAULT DATE '2000-01-01',
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_benchmarks_slice
    ON benchmarks (scope, segment, metric, period, region) WHERE is_active;
CREATE TRIGGER trg_benchmarks_updated
    BEFORE UPDATE ON benchmarks
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================================
-- MODEL RUNS  (one row per ML training/evaluation execution)
-- =============================================================================
CREATE TABLE model_runs (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_type     VARCHAR(16) NOT NULL CHECK (model_type IN ('PREDICTION','ANOMALY')),
    target_metric  VARCHAR(12),
    algorithm      VARCHAR(64) NOT NULL,
    params         JSONB NOT NULL DEFAULT '{}'::jsonb,
    metrics        JSONB NOT NULL DEFAULT '{}'::jsonb,   -- {"mae":..,"rmse":..,"r2":..,"cv_folds":..}
    training_rows  INTEGER NOT NULL DEFAULT 0,
    duration_ms    INTEGER NOT NULL DEFAULT 0,
    trained_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_model_runs_type_time ON model_runs (model_type, trained_at DESC);

-- =============================================================================
-- PREDICTIONS
-- =============================================================================
CREATE TABLE predictions (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    model_run_id          UUID REFERENCES model_runs(id) ON DELETE SET NULL,
    target_metric         VARCHAR(12) NOT NULL
                          CHECK (target_metric IN ('CO2E','ENERGY','WATER','SCORE')),
    generated_for_month   DATE NOT NULL,   -- last actual month used
    horizon_month         DATE NOT NULL,   -- month being predicted
    current_value         NUMERIC(16,4) NOT NULL,
    predicted_value       NUMERIC(16,4) NOT NULL,
    lower_bound           NUMERIC(16,4),
    upper_bound           NUMERIC(16,4),
    expected_change_abs   NUMERIC(16,4),
    expected_change_pct   NUMERIC(8,2),
    model_name            VARCHAR(64) NOT NULL,
    feature_importance    JSONB NOT NULL DEFAULT '[]'::jsonb,
    metrics               JSONB NOT NULL DEFAULT '{}'::jsonb,
    confidence_note       TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_predictions_user_metric_horizon
    ON predictions (user_id, target_metric, horizon_month DESC);

-- =============================================================================
-- ANOMALIES
-- =============================================================================
CREATE TABLE anomalies (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    environmental_record_id   UUID REFERENCES environmental_records(id) ON DELETE SET NULL,
    activity_type_id          UUID REFERENCES activity_types(id) ON DELETE SET NULL,
    period_month              DATE NOT NULL,
    metric                    VARCHAR(16) NOT NULL
                              CHECK (metric IN ('ELECTRICITY','WATER','FUEL','WASTE','CO2E','ENERGY','TRANSPORT')),
    method                    VARCHAR(20) NOT NULL
                              CHECK (method IN ('ZSCORE','IQR','STL_RESIDUAL','ISOLATION_FOREST','CONSENSUS')),
    observed_value            NUMERIC(16,4) NOT NULL,
    baseline_value            NUMERIC(16,4) NOT NULL,
    deviation_abs             NUMERIC(16,4) NOT NULL,
    deviation_pct             NUMERIC(8,2)  NOT NULL,
    score                     NUMERIC(10,4) NOT NULL,
    severity                  VARCHAR(8) NOT NULL CHECK (severity IN ('LOW','MEDIUM','HIGH')),
    direction                 VARCHAR(6) NOT NULL CHECK (direction IN ('SPIKE','DROP')),
    estimated_co2e_impact_kg  NUMERIC(16,4),
    explanation               TEXT NOT NULL,
    status                    VARCHAR(14) NOT NULL DEFAULT 'OPEN'
                              CHECK (status IN ('OPEN','ACKNOWLEDGED','RESOLVED','DISMISSED')),
    reviewed_by               UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at               TIMESTAMPTZ,
    detected_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_anomalies_user_period ON anomalies (user_id, period_month);
CREATE INDEX ix_anomalies_user_status ON anomalies (user_id, status);

-- =============================================================================
-- SCENARIOS  (what-if)
-- =============================================================================
CREATE TABLE scenarios (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name               VARCHAR(160) NOT NULL,
    description        TEXT,
    base_period_from   DATE NOT NULL,
    base_period_to     DATE NOT NULL,
    baseline_snapshot  JSONB NOT NULL DEFAULT '{}'::jsonb,
    adjustments        JSONB NOT NULL DEFAULT '[]'::jsonb,
    result             JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_scenario_window CHECK (base_period_to >= base_period_from)
);
CREATE INDEX ix_scenarios_user_created ON scenarios (user_id, created_at DESC);
CREATE TRIGGER trg_scenarios_updated
    BEFORE UPDATE ON scenarios
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================================
-- RECOMMENDATIONS
-- =============================================================================
CREATE TABLE recommendations (
    id                           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    period_month                 DATE NOT NULL,
    rank                         INTEGER NOT NULL,
    category                     VARCHAR(20) NOT NULL,
    title                        VARCHAR(200) NOT NULL,
    rationale                    TEXT NOT NULL,
    action_type                  VARCHAR(24) NOT NULL
                                 CHECK (action_type IN ('REDUCE_CONSUMPTION','SWITCH_SOURCE','EFFICIENCY',
                                                        'MODAL_SHIFT','RECYCLING','BEHAVIOURAL')),
    assumed_change               JSONB NOT NULL DEFAULT '{}'::jsonb,
    estimated_reduction_co2e_kg  NUMERIC(16,4) NOT NULL DEFAULT 0,
    estimated_reduction_pct      NUMERIC(8,2)  NOT NULL DEFAULT 0,
    estimated_other              JSONB NOT NULL DEFAULT '{}'::jsonb,
    confidence                   VARCHAR(8) NOT NULL DEFAULT 'MEDIUM'
                                 CHECK (confidence IN ('LOW','MEDIUM','HIGH')),
    basis                        VARCHAR(12) NOT NULL DEFAULT 'DATA_DRIVEN'
                                 CHECK (basis IN ('DATA_DRIVEN','GENERIC')),
    source                       VARCHAR(14) NOT NULL DEFAULT 'RULE_ENGINE'
                                 CHECK (source IN ('RULE_ENGINE','LLM_ASSISTED')),
    status                       VARCHAR(12) NOT NULL DEFAULT 'SUGGESTED'
                                 CHECK (status IN ('SUGGESTED','ACCEPTED','IN_PROGRESS','DONE','DISMISSED')),
    generated_at                 TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_recommendations_user_period_rank ON recommendations (user_id, period_month, rank);

-- =============================================================================
-- REPORTS
-- =============================================================================
CREATE TABLE reports (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    organization_id  UUID REFERENCES organizations(id) ON DELETE SET NULL,
    title            VARCHAR(200) NOT NULL,
    period_from      DATE NOT NULL,
    period_to        DATE NOT NULL,
    format           VARCHAR(6) NOT NULL DEFAULT 'PDF' CHECK (format IN ('JSON','PDF')),
    status           VARCHAR(12) NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING','GENERATING','READY','FAILED')),
    payload          JSONB NOT NULL DEFAULT '{}'::jsonb,
    file_path        VARCHAR(400),
    file_size_bytes  BIGINT,
    generated_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_report_window CHECK (period_to >= period_from)
);
CREATE INDEX ix_reports_user_created ON reports (user_id, created_at DESC);

-- =============================================================================
-- AI ASSISTANT  (conversations + messages)
-- =============================================================================
CREATE TABLE ai_conversations (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title        VARCHAR(200) NOT NULL DEFAULT 'New conversation',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_ai_conversations_user ON ai_conversations (user_id, updated_at DESC);
CREATE TRIGGER trg_ai_conversations_updated
    BEFORE UPDATE ON ai_conversations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE ai_messages (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id   UUID NOT NULL REFERENCES ai_conversations(id) ON DELETE CASCADE,
    role              VARCHAR(10) NOT NULL CHECK (role IN ('USER','ASSISTANT','SYSTEM')),
    content           TEXT NOT NULL,
    context_snapshot  JSONB,
    model             VARCHAR(80),
    tokens            INTEGER,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_ai_messages_conv ON ai_messages (conversation_id, created_at);

-- =============================================================================
-- AUDIT LOG  (append-only)
-- =============================================================================
CREATE TABLE audit_log (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID REFERENCES users(id) ON DELETE SET NULL,
    action       VARCHAR(80) NOT NULL,
    entity_type  VARCHAR(60),
    entity_id    UUID,
    ip_address   VARCHAR(64),
    user_agent   VARCHAR(255),
    metadata     JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_user_time    ON audit_log (user_id, created_at DESC);
CREATE INDEX ix_audit_entity       ON audit_log (entity_type, entity_id);

-- =============================================================================
-- End of V1
-- =============================================================================

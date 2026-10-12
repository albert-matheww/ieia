-- =============================================================================
-- IEIA — demo accounts (optional, standalone)
-- =============================================================================
-- The FULL demo dataset (24 months of activity across every category, with
-- injected trends and anomalies, plus the derived impacts, monthly metrics,
-- environmental scores, predictions, anomalies and recommendations) is built by
-- the backend on startup when IEIA_SEED_DEMO=true — see
-- backend/src/main/java/com/ieia/demo/DemoDataSeeder.java. That path is
-- authoritative because every record is run through the real calculation and
-- scoring pipeline.
--
-- This script only creates the three login accounts, so a database populated by
-- `psql` alone still has users you can authenticate as. Run it AFTER V1 + V2.
--
--   Password for all three:  Demo!Pass1
--   BCrypt(12) hash below; Spring Security accepts the $2y$ prefix.
-- =============================================================================

INSERT INTO users (email, password_hash, full_name, role, region, is_active)
VALUES
  ('admin@ieia.demo',       '$2y$12$5KtE7ocEsVUhsXjBXUgT.uAKQdU2L49LhuukriFgwY/FYPCbuYIbu', 'IEIA Admin',   'ADMIN',  'GLOBAL', TRUE),
  ('alex@ieia.demo',        '$2y$12$5KtE7ocEsVUhsXjBXUgT.uAKQdU2L49LhuukriFgwY/FYPCbuYIbu', 'Alex Rivera',  'MEMBER', 'IN',     TRUE)
ON CONFLICT DO NOTHING;

-- Organisation demo account
INSERT INTO organizations (name, industry, country, region, size_category, headcount)
VALUES ('Greenfield Labs', 'Research', 'US', 'GLOBAL', 'MEDIUM', 60)
ON CONFLICT DO NOTHING;

INSERT INTO users (email, password_hash, full_name, role, organization_id, region, is_active)
SELECT 'owner@greenfield.demo', '$2y$12$5KtE7ocEsVUhsXjBXUgT.uAKQdU2L49LhuukriFgwY/FYPCbuYIbu',
       'Sam Okoye', 'ORG_OWNER', o.id, 'GLOBAL', TRUE
FROM organizations o
WHERE o.name = 'Greenfield Labs'
ON CONFLICT DO NOTHING;

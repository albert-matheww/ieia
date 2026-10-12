#!/usr/bin/env bash
# Start the full stack locally WITHOUT Docker — an isolated PostgreSQL cluster,
# the FastAPI ML service, the Spring Boot backend and the Vite dev server.
# Requires: PostgreSQL 15+ client tools, JDK 17 (SDKMAN or JAVA_HOME), Python 3.12, Node 20+.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$(pwd)"

export LC_ALL=C LANG=C
PGDATA="${PGDATA:-/tmp/ieia-pg}"
PGPORT="${PGPORT:-55433}"
JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/17.0.16-tem}"
export JAVA_HOME

pids=()
cleanup() {
  echo; echo "Shutting down…"
  for p in "${pids[@]:-}"; do kill "$p" 2>/dev/null || true; done
  pg_ctl -D "$PGDATA" -w stop >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

# --- PostgreSQL (isolated cluster) ---
if [ ! -d "$PGDATA" ]; then
  echo "Creating PostgreSQL cluster in $PGDATA"
  initdb -D "$PGDATA" -U ieia --auth=trust --locale=C >/dev/null
fi
pg_ctl -D "$PGDATA" -o "-p $PGPORT -k /tmp" -l /tmp/ieia-pg.log -w start
export PGHOST=127.0.0.1 PGPORT=$PGPORT PGUSER=ieia
psql -tc "SELECT 1 FROM pg_database WHERE datname='ieia'" | grep -q 1 || createdb ieia
psql -d ieia -c "ALTER ROLE ieia PASSWORD 'ieia';" >/dev/null

# --- ML service ---
echo "Starting ML service on :8000"
( cd ml-service && REQUIRE_TOKEN=true ML_SERVICE_TOKEN=devtok ML_RANDOM_SEED=42 \
    .venv/bin/uvicorn app.main:app --host 127.0.0.1 --port 8000 ) &
pids+=($!)

# --- Backend ---
echo "Starting backend on :8080"
( cd backend && \
  SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:$PGPORT/ieia" \
  SPRING_DATASOURCE_USERNAME=ieia SPRING_DATASOURCE_PASSWORD=ieia \
  JWT_SECRET="local-dev-secret-local-dev-secret-0123456789abcd" \
  IEIA_SEED_DEMO=true SERVER_PORT=8080 \
  ML_SERVICE_URL=http://127.0.0.1:8000 ML_SERVICE_TOKEN=devtok \
  IEIA_LLM_PROVIDER=MOCK REPORT_OUTPUT_DIR=/tmp/ieia-reports \
  FRONTEND_ORIGIN=http://localhost:5173 \
  ./mvnw -q spring-boot:run ) &
pids+=($!)

# --- Frontend ---
echo "Starting frontend dev server on :5173"
( cd frontend && VITE_PROXY_TARGET=http://localhost:8080 npm run dev ) &
pids+=($!)

echo
echo "  Frontend  http://localhost:5173"
echo "  Backend   http://localhost:8080/api   (Swagger: /swagger-ui.html)"
echo "  ML        http://localhost:8000/docs"
echo "  Demo logins: alex@ieia.demo / admin@ieia.demo / owner@greenfield.demo  (Demo!Pass1)"
echo
wait

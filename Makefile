# IEIA — common tasks. `make help` lists them.
SHELL := /bin/bash
JAVA17 ?= $(HOME)/.sdkman/candidates/java/17.0.16-tem

.PHONY: help up down logs seed-reset backend ml frontend test test-backend test-ml test-frontend build

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

up: ## Start the whole stack in Docker (build + run)
	cp -n .env.example .env || true
	docker compose up --build

up-dev: ## Start the stack plus Adminer (DB browser on :8081)
	cp -n .env.example .env || true
	docker compose --profile dev up --build

down: ## Stop the stack (keep volumes)
	docker compose down

down-clean: ## Stop the stack and delete volumes (wipes the database)
	docker compose down -v

logs: ## Tail all service logs
	docker compose logs -f --tail=100

test: test-backend test-ml test-frontend ## Run every test suite

test-backend: ## Backend unit + integration tests (needs Docker for Testcontainers)
	cd backend && JAVA_HOME=$(JAVA17) ./mvnw -B verify

test-backend-unit: ## Backend unit tests only (no Docker needed)
	cd backend && JAVA_HOME=$(JAVA17) ./mvnw -B test -Dtest='!*IT'

test-ml: ## ML service tests
	cd ml-service && .venv/bin/python -m pytest -q

test-frontend: ## Frontend tests
	cd frontend && npm run test

build: ## Production build of every service artefact
	cd backend && JAVA_HOME=$(JAVA17) ./mvnw -B -DskipTests package
	cd ml-service && .venv/bin/python -c "import app.main"  # import-check
	cd frontend && npm run build

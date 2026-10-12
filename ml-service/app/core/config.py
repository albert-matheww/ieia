"""Environment-driven configuration (docs/01-architecture.md §3.3)."""
from __future__ import annotations

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="", case_sensitive=False)

    # Shared secret checked on every request (backend -> ml-service, internal network).
    ml_service_token: str = "dev-ml-token"
    # When empty, auth is disabled (useful for local `uvicorn --reload` and tests).
    require_token: bool = True

    # Global seed so every model run is reproducible.
    ml_random_seed: int = 42

    log_level: str = "INFO"

    # Guardrails
    min_history_for_model: int = 5      # fewer points -> SeasonalNaive fallback
    seasonality_period: int = 12
    max_horizon: int = 6


@lru_cache
def get_settings() -> Settings:
    return Settings()

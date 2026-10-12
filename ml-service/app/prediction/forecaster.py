"""Environmental-impact forecasting (docs/05-ml-methodology.md §2).

An interpretable model zoo (LinearRegression / RandomForest / GradientBoosting)
is compared by expanding-window time-series cross-validation; the best by MAE is
refit on all data. Short series fall back to SeasonalNaive with a clear note.
Deterministic given ``seed``.
"""
from __future__ import annotations

import warnings

import numpy as np
import pandas as pd
from sklearn.ensemble import GradientBoostingRegressor, RandomForestRegressor
from sklearn.linear_model import LinearRegression
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
from sklearn.model_selection import TimeSeriesSplit

from app.preprocessing.timeseries import (
    FEATURE_COLUMNS,
    future_feature_row,
    make_features,
    next_month,
    to_frame,
    training_matrix,
)
from app.preprocessing.validation import validate_series
from app.schemas.common import SeriesPoint
from app.schemas.prediction import (
    Evaluation,
    FeatureImportance,
    ForecastPoint,
    PredictRequest,
    PredictResponse,
)

_LIMITATIONS = [
    "Emission factors are assumed constant over the forecast horizon.",
    "No exogenous drivers (weather, price, occupancy, production volume) are modelled.",
    "Monthly granularity means the model sees few observations; treat point forecasts as indicative.",
]


def _model_zoo(seed: int) -> dict:
    return {
        "LinearRegression": LinearRegression(),
        "RandomForestRegressor": RandomForestRegressor(
            n_estimators=200, max_depth=4, random_state=seed, n_jobs=1
        ),
        "GradientBoostingRegressor": GradientBoostingRegressor(
            n_estimators=200, max_depth=2, learning_rate=0.05, random_state=seed
        ),
    }


def _cross_validate(model, X: pd.DataFrame, y: pd.Series, n_splits: int) -> tuple[float, float, float, int, list[float]]:
    """Expanding-window CV. MAE/RMSE are averaged per fold; R^2 is computed once
    over the pooled out-of-fold predictions (stable even with tiny test folds)."""
    maes, rmses, residuals = [], [], []
    y_true_all, y_pred_all = [], []
    tscv = TimeSeriesSplit(n_splits=n_splits)
    for train_idx, test_idx in tscv.split(X):
        with warnings.catch_warnings():
            warnings.simplefilter("ignore")
            model.fit(X.iloc[train_idx], y.iloc[train_idx])
            pred = model.predict(X.iloc[test_idx])
        maes.append(mean_absolute_error(y.iloc[test_idx], pred))
        rmses.append(float(np.sqrt(mean_squared_error(y.iloc[test_idx], pred))))
        residuals.extend((y.iloc[test_idx].to_numpy() - pred).tolist())
        y_true_all.extend(y.iloc[test_idx].tolist())
        y_pred_all.extend(np.asarray(pred).tolist())

    if len(y_true_all) >= 3 and np.var(y_true_all) > 1e-9:
        r2 = float(r2_score(y_true_all, y_pred_all))
    else:
        r2 = float("nan")
    return float(np.mean(maes)), float(np.mean(rmses)), r2, n_splits, residuals


def _seasonal_naive(df_feat: pd.DataFrame, horizon: int, seasonality: int) -> list[float]:
    y = df_feat["y"].to_numpy()
    preds = []
    hist = list(y)
    for _ in range(horizon):
        if len(hist) >= seasonality:
            preds.append(hist[-seasonality])
        else:
            preds.append(hist[-1])
        hist.append(preds[-1])
    return preds


def _feature_importance(model, name: str) -> list[FeatureImportance]:
    if hasattr(model, "feature_importances_"):
        vals = np.asarray(model.feature_importances_, dtype=float)
    elif hasattr(model, "coef_"):
        coef = np.abs(np.asarray(model.coef_, dtype=float)).ravel()
        vals = coef / coef.sum() if coef.sum() > 0 else coef
    else:  # pragma: no cover
        return []
    pairs = sorted(zip(FEATURE_COLUMNS, vals), key=lambda p: p[1], reverse=True)
    return [FeatureImportance(feature=f, importance=round(float(v), 4)) for f, v in pairs]


def forecast(req: PredictRequest, seed: int) -> PredictResponse:
    points: list[SeriesPoint] = validate_series(req.series, name="series", min_points=3)
    n_history = len(points)

    df = to_frame(points)
    df_feat = make_features(df)
    X, y = training_matrix(df_feat)

    future_period = next_month(points[-1].period)
    last_value = float(df_feat["y"].iloc[-1])

    # -- short series -> SeasonalNaive fallback -------------------------------
    if n_history < req.min_history or len(X) < 4:
        preds = _seasonal_naive(df_feat, req.horizon, req.seasonality)
        recent = df_feat["y"].to_numpy()
        sigma = float(np.std(np.diff(recent))) if len(recent) >= 3 else 0.15 * max(abs(last_value), 1.0)
        forecast_points = _build_forecast(future_period, preds, sigma)
        return PredictResponse(
            target=req.target,
            model="SeasonalNaive",
            forecast=forecast_points,
            evaluation=Evaluation(cv_folds=0, n_train=len(X)),
            feature_importance=[],
            n_history=n_history,
            note=(
                f"Only {n_history} months of history — using a seasonal-naive baseline "
                f"(repeat last year's value, or the last value). Not a trained model."
            ),
            limitations=_LIMITATIONS,
            seed=seed,
        )

    # -- model zoo + expanding-window CV -----------------------------------
    n_splits = max(2, min(5, len(X) - 3))
    scored = []
    for name, model in _model_zoo(seed).items():
        mae, rmse, r2, folds, residuals = _cross_validate(model, X, y, n_splits)
        scored.append((mae, rmse, r2, folds, residuals, name, model))
    scored.sort(key=lambda t: t[0])
    mae, rmse, r2, folds, residuals, best_name, best_model = scored[0]

    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        best_model.fit(X, y)

    # iterative multi-step forecast
    preds = []
    working = df_feat.copy()
    period_cursor = future_period
    for _ in range(req.horizon):
        row = future_feature_row(working, period_cursor)
        yhat = float(best_model.predict(row)[0])
        yhat = max(yhat, 0.0)
        preds.append(yhat)
        new_ts = pd.to_datetime(period_cursor + "-01")
        working = pd.concat(
            [working, pd.DataFrame({"y": [yhat], "period": [period_cursor]}, index=[new_ts])]
        )
        period_cursor = next_month(period_cursor)

    resid_arr = np.asarray(residuals, dtype=float)
    sigma = float(np.std(resid_arr)) if resid_arr.size >= 3 else 0.15 * max(abs(last_value), 1.0)
    forecast_points = _build_forecast(future_period, preds, sigma)

    interval_pct = round(100 * 1.96 * sigma / max(abs(preds[0]), 1e-6), 1)
    note = (
        f"Chosen by {folds}-fold time-series CV over Linear/RandomForest/GradientBoosting. "
        f"Based on {n_history} months; ±{interval_pct}% interval (1.96·σ of CV residuals). "
        f"Treat as indicative, not exact."
    )

    return PredictResponse(
        target=req.target,
        model=best_name,
        forecast=forecast_points,
        evaluation=Evaluation(
            mae=round(mae, 4),
            rmse=round(rmse, 4),
            r2=None if np.isnan(r2) else round(r2, 4),
            cv_folds=folds,
            n_train=len(X),
        ),
        feature_importance=_feature_importance(best_model, best_name),
        n_history=n_history,
        note=note,
        limitations=_LIMITATIONS,
        seed=seed,
    )


def _build_forecast(first_period: str, preds: list[float], sigma: float) -> list[ForecastPoint]:
    out = []
    period = first_period
    for i, p in enumerate(preds):
        widen = 1.0 + 0.25 * i  # interval grows further out
        half = 1.96 * sigma * widen
        out.append(
            ForecastPoint(
                period=period,
                value=round(p, 4),
                lower=round(max(p - half, 0.0), 4),
                upper=round(p + half, 4),
            )
        )
        period = next_month(period)
    return out

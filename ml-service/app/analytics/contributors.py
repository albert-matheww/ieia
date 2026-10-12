"""Exact contribution arithmetic (docs/05-ml-methodology.md §4). No ML."""
from __future__ import annotations

from collections import defaultdict

from app.schemas.analytics import ContributorShare, ContributorsRequest, ContributorsResponse


def _shares(totals: dict[str, float], grand: float) -> list[ContributorShare]:
    items = [
        ContributorShare(
            key=k,
            label=k,
            co2e_kg=round(v, 4),
            share_pct=round((v / grand * 100.0) if grand else 0.0, 2),
        )
        for k, v in totals.items()
    ]
    items.sort(key=lambda s: s.co2e_kg, reverse=True)
    return items


def contributions(req: ContributorsRequest) -> ContributorsResponse:
    by_cat: dict[str, float] = defaultdict(float)
    by_act: dict[str, float] = defaultdict(float)
    for row in req.impacts:
        by_cat[row.category] += row.co2e_kg
        if row.activity_code:
            by_act[row.activity_code] += row.co2e_kg

    grand = sum(by_cat.values())
    cat_shares = _shares(dict(by_cat), grand)
    act_shares = _shares(dict(by_act), grand)
    return ContributorsResponse(
        total_co2e_kg=round(grand, 4),
        by_category=cat_shares,
        by_activity=act_shares,
        top_contributor=cat_shares[0] if cat_shares else None,
    )

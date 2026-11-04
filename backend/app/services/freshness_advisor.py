"""Safe, lot-grounded inventory recovery suggestions for store staff."""

from __future__ import annotations

import json
from datetime import date
from typing import Any
from urllib.error import URLError
from urllib.request import Request, urlopen

from app.config import settings
from app.schemas import FreshnessAdvice, FreshnessAdviceAction


_ALLOWED_BY_STATUS = {
    "near_expiry": {"markdown_review", "donation_review"},
    "expired": {"quarantine_review"},
}


def _days_remaining(lot: dict[str, Any]) -> int:
    return (date.fromisoformat(lot["expires_on"]) - date.today()).days


def _rule_actions(lots: list[dict[str, Any]]) -> list[FreshnessAdviceAction]:
    actions = []
    for lot in lots:
        status = lot.get("status")
        if status == "expired":
            actions.append(FreshnessAdviceAction(
                lot_code=lot["lot_code"], recommendation="quarantine_review",
                reason="Remove from dispatch and follow the store's expired-stock handling procedure.",
            ))
        elif status == "near_expiry":
            if _days_remaining(lot) <= 1:
                recommendation = "donation_review"
                reason = "Review donation eligibility and local handling rules before the lot expires."
            else:
                recommendation = "markdown_review"
                reason = "Ask a supervisor whether an approved markdown can help recover this short-dated stock."
            actions.append(FreshnessAdviceAction(
                lot_code=lot["lot_code"], recommendation=recommendation, reason=reason,
            ))
    return actions


def _request_gemini(lots: list[dict[str, Any]]) -> tuple[str, list[FreshnessAdviceAction]]:
    model = settings.gemini_model
    request_schema = {
        "type": "OBJECT",
        "properties": {
            "summary": {"type": "STRING"},
            "actions": {
                "type": "ARRAY",
                "items": {
                    "type": "OBJECT",
                    "properties": {
                        "lot_code": {"type": "STRING"},
                        "recommendation": {"type": "STRING", "enum": ["markdown_review", "donation_review", "quarantine_review"]},
                        "reason": {"type": "STRING"},
                    },
                    "required": ["lot_code", "recommendation", "reason"],
                },
            },
        },
        "required": ["summary", "actions"],
    }
    input_rows = [{key: lot[key] for key in ("sku", "product_name", "lot_code", "expires_on", "quantity", "status")}
                  for lot in lots if lot.get("status") in _ALLOWED_BY_STATUS]
    prompt = (
        "You advise a quick-commerce dark-store supervisor reviewing short-dated grocery stock. "
        "Use only the supplied facts. Return concise, cautious operational suggestions for the exact lot codes. "
        "For near_expiry lots suggest markdown_review or donation_review and ask staff to check store policy. "
        "For expired lots suggest only quarantine_review. Never claim food is safe, invent discounts, prices, "
        "laws, or quantities, and never recommend changing inventory automatically. Do not suggest safe lots. "
        "If there are no lots, summarize that no recovery action is needed.\n\n"
        f"Lots: {json.dumps(input_rows, separators=(',', ':'))}"
    )
    payload = json.dumps({
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {"responseMimeType": "application/json", "responseSchema": request_schema},
    }).encode("utf-8")
    request = Request(
        f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent",
        data=payload,
        headers={"Content-Type": "application/json", "x-goog-api-key": settings.gemini_api_key or ""},
        method="POST",
    )
    with urlopen(request, timeout=15) as response:
        body = json.loads(response.read().decode("utf-8"))
    text = body["candidates"][0]["content"]["parts"][0]["text"]
    result = json.loads(text)

    by_code = {lot["lot_code"]: lot for lot in lots}
    safe_actions: list[FreshnessAdviceAction] = []
    seen: set[str] = set()
    for raw in result.get("actions", []):
        if not isinstance(raw, dict):
            continue
        code = raw.get("lot_code")
        recommendation = raw.get("recommendation")
        lot = by_code.get(code)
        if (lot is None or code in seen or
                recommendation not in _ALLOWED_BY_STATUS.get(lot.get("status"), set())):
            continue
        reason = raw.get("reason")
        if not isinstance(reason, str) or not reason.strip():
            continue
        safe_actions.append(FreshnessAdviceAction(
            lot_code=code,
            recommendation=recommendation,
            reason=reason.strip()[:240],
        ))
        seen.add(code)
    summary = result.get("summary")
    if not isinstance(summary, str) or not summary.strip():
        raise ValueError("Gemini did not return an advisor summary")
    return summary.strip()[:240], safe_actions


def advise_on_freshness(lots: list[dict[str, Any]]) -> FreshnessAdvice:
    if settings.gemini_api_key:
        try:
            summary, actions = _request_gemini(lots)
            return FreshnessAdvice(provider="gemini", model=settings.gemini_model, summary=summary, actions=actions)
        except (OSError, URLError, ValueError, KeyError, IndexError, TypeError, json.JSONDecodeError):
            # The rules path is intentionally useful if the provider is unavailable or misconfigured.
            pass

    actions = _rule_actions(lots)
    near_count = sum(lot.get("status") == "near_expiry" for lot in lots)
    expired_count = sum(lot.get("status") == "expired" for lot in lots)
    if expired_count:
        summary = f"{expired_count} expired lot(s) need quarantine review. Keep them out of dispatch."
    elif near_count:
        summary = f"{near_count} short-dated lot(s) need supervisor review for an approved recovery option."
    else:
        summary = "No short-dated or expired lots need recovery review right now."
    return FreshnessAdvice(provider="rules", summary=summary, actions=actions)

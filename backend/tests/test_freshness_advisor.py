import json
import unittest
from datetime import date, timedelta
from unittest.mock import patch

from app.config import settings
from app.services.freshness_advisor import advise_on_freshness


def lot(code: str, status: str, days: int) -> dict:
    return {
        "sku": "MILK-500",
        "product_name": "Milk 500 ml",
        "lot_code": code,
        "expires_on": (date.today() + timedelta(days=days)).isoformat(),
        "quantity": 2,
        "status": status,
    }


class FreshnessAdvisorTests(unittest.TestCase):
    def setUp(self):
        self.previous_key = settings.gemini_api_key
        self.previous_model = settings.gemini_model

    def tearDown(self):
        settings.gemini_api_key = self.previous_key
        settings.gemini_model = self.previous_model

    def test_rules_fallback_only_suggests_safe_review_types(self):
        settings.gemini_api_key = None
        result = advise_on_freshness([
            lot("MILK-NEAR", "near_expiry", 1),
            lot("MILK-OLD", "expired", -1),
            lot("MILK-SAFE", "safe", 8),
        ])
        self.assertEqual("rules", result.provider)
        self.assertEqual({"MILK-NEAR", "MILK-OLD"}, {action.lot_code for action in result.actions})
        self.assertEqual("donation_review", next(a for a in result.actions if a.lot_code == "MILK-NEAR").recommendation)
        self.assertEqual("quarantine_review", next(a for a in result.actions if a.lot_code == "MILK-OLD").recommendation)

    def test_gemini_output_is_limited_to_real_lots_and_status_appropriate_actions(self):
        settings.gemini_api_key = "test-key"
        settings.gemini_model = "test-model"
        lots = [lot("MILK-NEAR", "near_expiry", 2), lot("MILK-SAFE", "safe", 8)]
        candidate = {"candidates": [{"content": {"parts": [{"text": json.dumps({
            "summary": "Review short-dated stock.",
            "actions": [
                {"lot_code": "MILK-NEAR", "recommendation": "markdown_review", "reason": "Ask supervisor."},
                {"lot_code": "MILK-SAFE", "recommendation": "quarantine_review", "reason": "Invented risk."},
                {"lot_code": "MADE-UP", "recommendation": "donation_review", "reason": "Unknown lot."},
            ],
        })}]}}]}
        class Response:
            def __enter__(self): return self
            def __exit__(self, *_): return False
            def read(self): return json.dumps(candidate).encode()

        with patch("app.services.freshness_advisor.urlopen", return_value=Response()) as open_url:
            result = advise_on_freshness(lots)
        self.assertEqual("gemini", result.provider)
        self.assertEqual(["MILK-NEAR"], [action.lot_code for action in result.actions])
        headers = {key.lower(): value for key, value in open_url.call_args.args[0].headers.items()}
        self.assertEqual("test-key", headers["x-goog-api-key"])

    def test_provider_error_falls_back_to_rules(self):
        settings.gemini_api_key = "test-key"
        with patch("app.services.freshness_advisor.urlopen", side_effect=TimeoutError("offline")):
            result = advise_on_freshness([lot("MILK-NEAR", "near_expiry", 2)])
        self.assertEqual("rules", result.provider)
        self.assertEqual(["MILK-NEAR"], [action.lot_code for action in result.actions])


if __name__ == "__main__":
    unittest.main()

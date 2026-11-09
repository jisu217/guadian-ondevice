import unittest

from guadian_ml import paths
from guadian_ml.llm.contract.verdict_table import Facts, VerdictTable

TABLE = VerdictTable.load()
NOTHING = dict(apk_link=False, sensitive_field=False, personal_field=False)
QUIET = dict(claim="none", sign_in=False, payment=False, investment=False, reward_pressure=False, content_farm=False)


def page(**overrides):
    return Facts(**{"opened": True, "official": False, **NOTHING, **overrides})


def answered(**overrides):
    return Facts(**{"opened": True, "official": False, **NOTHING, **QUIET, **overrides})


class VerdictTableTest(unittest.TestCase):
    def decide(self, facts):
        d = TABLE.decide(facts)
        return d.risk, d.type, d.final

    def test_gate_a_stops_only_on_high(self):
        self.assertEqual(self.decide(Facts(blocklisted=True)), ("HIGH", "blocklisted", True))
        self.assertEqual(self.decide(Facts(host_imitation="certain")), ("HIGH", "impersonation", True))
        self.assertFalse(TABLE.decide(Facts()).final)
        self.assertFalse(TABLE.decide(Facts(host_imitation="suspicious")).final)

    def test_official_landing_is_low_and_final_whatever_it_shows(self):
        self.assertEqual(self.decide(page(official=True)), ("LOW", "none", True))
        self.assertEqual(self.decide(page(official=True, apk_link=True, sensitive_field=True)), ("LOW", "none", True))

    def test_sensitive_field_or_apk_off_the_brand_site_is_high_before_the_model(self):
        self.assertEqual(self.decide(page(sensitive_field=True)), ("HIGH", "credentials", True))
        self.assertEqual(self.decide(page(apk_link=True)), ("HIGH", "apk", True))

    def test_a_personal_field_waits_for_the_model(self):
        self.assertEqual(self.decide(page(personal_field=True)), ("MEDIUM", "personal_info", False))
        self.assertEqual(self.decide(answered(personal_field=True)), ("MEDIUM", "personal_info", True))
        self.assertEqual(self.decide(answered(personal_field=True, claim="listed_other")), ("HIGH", "impersonation", True))

    def test_unlisted_claim_is_impersonation_only_with_a_sign_in_screen_or_sensitive_field(self):
        self.assertEqual(self.decide(answered(claim="unlisted")), ("LOW", "none", True))
        self.assertEqual(self.decide(answered(claim="unlisted", sign_in=True)), ("HIGH", "impersonation", True))

    def test_suspicious_host_is_at_least_medium(self):
        self.assertEqual(self.decide(answered(host_imitation="suspicious")), ("MEDIUM", "impersonation", True))

    def test_unopened_page_is_unverifiable(self):
        self.assertEqual(self.decide(Facts(opened=False, official=False, **NOTHING)), ("MEDIUM", "unverifiable", True))

    def test_design_document_shows_this_table(self):
        design = (paths.ML_DIR.parent / "docs" / "design.md").read_text(encoding="utf-8")
        self.assertIn(TABLE.markdown(), design)


if __name__ == "__main__":
    unittest.main()

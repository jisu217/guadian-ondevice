import csv
import unittest
from pathlib import Path

from url_features import FEATURE_NAMES, extract_features, feature_vector


class UrlFeatureTest(unittest.TestCase):
    def test_extracts_apk_risk_features(self):
        features = extract_features("https://unknown-site.test/download/app.apk")

        self.assertEqual(features["has_apk"], 1.0)
        self.assertEqual(features["suspicious_tld"], 1.0)
        self.assertEqual(features["official_domain_match"], 0.0)

    def test_extracts_login_brand_features(self):
        features = extract_features("https://bank-login-verification.test/account")

        self.assertEqual(features["has_login"], 1.0)
        self.assertEqual(features["brand_keyword_present"], 1.0)

    def test_matches_official_domain(self):
        features = extract_features("https://support.example.com/help")

        self.assertEqual(features["official_domain_match"], 1.0)
        self.assertGreater(features["subdomain_count"], 0.0)

    def test_feature_vector_order_matches_feature_names(self):
        vector = feature_vector("https://promo-reward-event.test/free-gift")

        self.assertEqual(len(vector), len(FEATURE_NAMES))
        self.assertTrue(all(isinstance(value, float) for value in vector))

    def test_sample_dataset_urls_are_extractable(self):
        sample_path = Path(__file__).parents[1] / "data" / "sample_urls.csv"
        with sample_path.open(newline="") as handle:
            rows = list(csv.DictReader(handle))

        self.assertGreater(len(rows), 0)
        for row in rows:
            features = extract_features(row["url"])
            self.assertEqual(set(features), set(FEATURE_NAMES))


if __name__ == "__main__":
    unittest.main()

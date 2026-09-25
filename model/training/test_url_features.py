import csv
import unittest
from pathlib import Path

from url_features import FEATURE_NAMES, extract_features, feature_vector


class UrlFeatureTest(unittest.TestCase):
    def test_extracts_apk_risk_features(self):
        features = extract_features("https://unknown-site.top/download/app.apk")

        self.assertEqual(features["has_apk"], 1.0)
        self.assertEqual(features["suspicious_tld"], 1.0)
        self.assertEqual(features["official_domain_match"], 0.0)

    def test_reserved_test_tld_is_not_a_risk_signal(self):
        # Synthetic rows use .test; treating it as suspicious leaked the
        # "synthetic" source into the HIGH label.
        features = extract_features("https://unknown-site.test/download/app.apk")

        self.assertEqual(features["suspicious_tld"], 0.0)
        self.assertEqual(features["has_shortener_token"], 0.0)

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

    def test_extracts_redirect_and_ad_network_features(self):
        features = extract_features(
            "https://googleads.g.doubleclick.net/pcs/click?"
            "adurl=https%3A%2F%2Fexample.com%2Flanding&gclid=abc123&utm_source=display"
        )

        self.assertEqual(features["query_param_count"], 3.0)
        self.assertEqual(features["utm_param_count"], 1.0)
        self.assertEqual(features["tracking_param_count"], 2.0)
        self.assertEqual(features["has_nested_url"], 1.0)
        self.assertEqual(features["has_encoded_url"], 1.0)
        self.assertEqual(features["has_ad_click_host"], 1.0)

    def test_extracts_base64_like_redirect_token(self):
        features = extract_features(
            "https://s.adscope.co.kr/adt_connect.php?"
            "u=aHR0cDovL3Nha3VuemEuY29tL25ld3NtL2luZGV4X2JuLmh0bWw&v=&d="
        )

        self.assertEqual(features["has_ad_click_host"], 1.0)
        self.assertEqual(features["has_base64_like_token"], 1.0)

    def test_feature_parity_fixture_matches_python_extractor(self):
        fixture_path = Path(__file__).parents[1] / "data" / "fixtures" / "feature_parity_cases.csv"
        with fixture_path.open(newline="") as handle:
            rows = list(csv.DictReader(handle))

        self.assertGreater(len(rows), 0)
        self.assertEqual([name for name in rows[0] if name != "url"], FEATURE_NAMES)

        for row in rows:
            expected = [float(row[name]) for name in FEATURE_NAMES]
            self.assertEqual(feature_vector(row["url"]), expected)

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

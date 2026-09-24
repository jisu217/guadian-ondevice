import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from build_official_domains import (
    BRAND_DOMAINS,
    SEED_DOMAINS,
    build_domain_set,
    load_tranco_domains,
    save_domains,
)


class BuildOfficialDomainsTest(unittest.TestCase):
    def test_loads_tranco_rows_up_to_rank_limit(self):
        with TemporaryDirectory() as tmp:
            tranco_path = Path(tmp) / "top-1m.csv"
            tranco_path.write_text("1,google.com\n2,cloudflare.com\n3,excluded.com\n")

            domains = load_tranco_domains(tranco_path, rank_limit=2)

            self.assertEqual(domains, {"google.com", "cloudflare.com"})

    def test_build_domain_set_includes_brands_and_seed_domains(self):
        with TemporaryDirectory() as tmp:
            tranco_path = Path(tmp) / "top-1m.csv"
            tranco_path.write_text("1,google.com\n")

            domains = build_domain_set(tranco_path, rank_limit=50_000)

            self.assertIn("google.com", domains)
            self.assertTrue(BRAND_DOMAINS.issubset(domains))
            self.assertTrue(SEED_DOMAINS.issubset(domains))

    def test_save_domains_writes_sorted_deduplicated_lines(self):
        with TemporaryDirectory() as tmp:
            out_path = Path(tmp) / "official_domains.txt"

            save_domains({"b.com", "a.com", "a.com"}, out_path)

            self.assertEqual(out_path.read_text(), "a.com\nb.com\n")


if __name__ == "__main__":
    unittest.main()

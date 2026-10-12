import unittest

from guadian_ml.llm.dataset.urls import keep, site_of


class KeepTest(unittest.TestCase):
    def test_sites_are_registrable_domains(self):
        self.assertEqual(site_of("https://m.shop.co.kr/a"), "shop.co.kr")
        self.assertNotEqual(site_of("https://a.vercel.app/"), site_of("https://b.vercel.app/"))

    def test_drops_known_sites_duplicates_and_caps_per_site(self):
        rows = [{"url": u} for u in ("https://a.com/1", "https://a.com/1", "https://www.a.com/2", "https://a.com/3",
                                     "https://known.com/", "https://b.com/")]
        self.assertEqual([r["url"] for r in keep(rows, {"known.com"}, per_site=2, limit=None)],
                         ["https://a.com/1", "https://www.a.com/2", "https://b.com/"])

    def test_stops_at_limit(self):
        rows = [{"url": f"https://s{i}.com/"} for i in range(5)]
        self.assertEqual(len(keep(rows, set(), per_site=1, limit=3)), 3)


if __name__ == "__main__":
    unittest.main()

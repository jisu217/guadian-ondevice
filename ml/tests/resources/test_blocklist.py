import unittest

from guadian_ml.resources.blocklist import BloomFilter, fnv1a64, parse_list


class BlocklistFilterTest(unittest.TestCase):
    def test_fnv1a64_matches_reference_values(self):
        # Published FNV-1a 64-bit test vectors.
        self.assertEqual(fnv1a64(""), 0xCBF29CE484222325)
        self.assertEqual(fnv1a64("a"), 0xAF63DC4C8601EC8C)

    def test_added_domains_are_found_and_others_are_not(self):
        bloom = BloomFilter.sized_for(1_000)
        for i in range(1_000):
            bloom.add(f"phish{i}.example")
        self.assertTrue(all(f"phish{i}.example" in bloom for i in range(1_000)))
        self.assertEqual(sum(f"safe{i}.example" in bloom for i in range(10_000)), 0)

    def test_bytes_round_trip(self):
        bloom = BloomFilter.sized_for(10)
        bloom.add("evil.example")
        restored = BloomFilter.from_bytes(bloom.to_bytes())
        self.assertEqual((restored.m, restored.k, restored.n), (bloom.m, bloom.k, 1))
        self.assertIn("evil.example", restored)

    def test_parse_list_reads_hosts_and_plain_lines(self):
        text = "# comment\n0.0.0.0 bad.example\ngood.example # note\nlocalhost\n127.0.0.1\n"
        self.assertEqual(parse_list(text), {"bad.example", "good.example"})


if __name__ == "__main__":
    unittest.main()

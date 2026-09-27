"""Build model/runtime/blocklist.bloom, the phishing/malware blocklist the app checks on the device.

    python3 model/training/build_blocklist_filter.py [--source FILE ...]

The source lists are the ones the original GUADIAN server used: ut1 phishing and
malware (https://github.com/smed79/ut1, CC BY-NC-SA 3.0, non-commercial). About
268,000 domains do not fit in an APK as text, so they are stored as a Bloom
filter sized for a one-in-a-million false-positive rate per lookup (~1 MB). At
that rate a hit is treated as certain.

Entries are dropped when they could block unrelated sites:
    - public suffixes (e.g. kirk.replit.dev): every site under them would match
    - domains in official_domains.txt: a popular site wrongly listed would block
      many users, and a false alarm costs more trust than a missed site

File format (big-endian):
    magic "GBL1" | version u8 = 1 | k u8 | reserved u16 | m u32 (bits) | n u32 (entries)
    then ceil(m / 8) bytes of bits; bit i lives in byte i >> 3 under mask 1 << (i & 7).

Bit positions use double hashing over 64-bit FNV-1a of the ASCII domain:
    h1 = fnv1a64(domain), h2 = splitmix64(h1) | 1, bit_i = (h1 + i * h2) mod m.
core/judge util/BlocklistFilter.kt implements the same scheme.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import random
import struct
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).parents[2]
RUNTIME = ROOT / "model" / "runtime"
DEFAULT_OUTPUT = RUNTIME / "blocklist.bloom"
DEFAULT_META = RUNTIME / "blocklist.meta.json"
DEFAULT_FIXTURE = ROOT / "model" / "data" / "fixtures" / "blocklist_filter_cases.csv"
OFFICIAL_DOMAINS = RUNTIME / "official_domains.txt"
PUBLIC_SUFFIX_LIST = RUNTIME / "public_suffix_list.dat"

SOURCES = {
    "ut1-phishing": "https://raw.githubusercontent.com/smed79/ut1/master/phishing/domains",
    "ut1-malware": "https://raw.githubusercontent.com/smed79/ut1/master/malware/domains",
}
FALSE_POSITIVE_RATE = 1e-6
MAGIC = b"GBL1"
VERSION = 1
MASK64 = (1 << 64) - 1
FNV_OFFSET = 0xCBF29CE484222325
FNV_PRIME = 0x100000001B3


def fnv1a64(text: str) -> int:
    h = FNV_OFFSET
    for byte in text.encode("ascii"):
        h = ((h ^ byte) * FNV_PRIME) & MASK64
    return h


def splitmix64(x: int) -> int:
    x = (x + 0x9E3779B97F4A7C15) & MASK64
    x = ((x ^ (x >> 30)) * 0xBF58476D1CE4E5B9) & MASK64
    x = ((x ^ (x >> 27)) * 0x94D049BB133111EB) & MASK64
    return x ^ (x >> 31)


def bit_positions(domain: str, k: int, m: int) -> list[int]:
    h1 = fnv1a64(domain)
    h2 = splitmix64(h1) | 1
    return [((h1 + i * h2) & MASK64) % m for i in range(k)]


class BloomFilter:
    def __init__(self, m: int, k: int, bits: bytearray | None = None, n: int = 0) -> None:
        self.m, self.k, self.n = m, k, n
        self.bits = bits if bits is not None else bytearray((m + 7) // 8)

    @classmethod
    def sized_for(cls, n: int, p: float = FALSE_POSITIVE_RATE) -> "BloomFilter":
        m = math.ceil(-n * math.log(p) / (math.log(2) ** 2))
        k = max(1, round(m / n * math.log(2)))
        return cls(m, k)

    def add(self, domain: str) -> None:
        for i in bit_positions(domain, self.k, self.m):
            self.bits[i >> 3] |= 1 << (i & 7)
        self.n += 1

    def __contains__(self, domain: str) -> bool:
        return all(self.bits[i >> 3] & (1 << (i & 7)) for i in bit_positions(domain, self.k, self.m))

    def to_bytes(self) -> bytes:
        return MAGIC + struct.pack(">BBHII", VERSION, self.k, 0, self.m, self.n) + bytes(self.bits)

    @classmethod
    def from_bytes(cls, data: bytes) -> "BloomFilter":
        if data[:4] != MAGIC:
            raise ValueError("not a GBL1 blocklist filter")
        version, k, _, m, n = struct.unpack(">BBHII", data[4:16])
        if version != VERSION:
            raise ValueError(f"unsupported filter version {version}")
        return cls(m, k, bytearray(data[16:]), n)


def parse_list(text: str) -> set[str]:
    """Domains from a hosts-style or one-domain-per-line list (as server/main.py _parse_list)."""
    domains = set()
    for line in text.splitlines():
        line = line.split("#", 1)[0].strip()
        if not line:
            continue
        parts = line.split()
        domain = (parts[-1] if len(parts) > 1 else parts[0]).lower().strip(".")
        if domain in ("localhost", "localhost.localdomain", "broadcasthost", "0.0.0.0", "::1"):
            continue
        if "." in domain and not domain.replace(".", "").isdigit() and domain.isascii():
            domains.add(domain)
    return domains


def public_suffixes(path: Path = PUBLIC_SUFFIX_LIST) -> set[str]:
    """Plain rules from the Public Suffix List (wildcard and exception rules are rare in blocklists)."""
    suffixes = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith(("//", "!", "*.")):
            suffixes.add(line.lower())
    return suffixes


def load_official(path: Path = OFFICIAL_DOMAINS) -> set[str]:
    return {line.strip() for line in path.read_text(encoding="utf-8").splitlines()
            if line.strip() and not line.startswith("#")}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--source", type=Path, action="append",
                        help="local list file instead of downloading (repeatable)")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--meta", type=Path, default=DEFAULT_META)
    parser.add_argument("--fixture", type=Path, default=DEFAULT_FIXTURE)
    args = parser.parse_args()

    if args.source:
        texts = {path.name: path.read_text(encoding="utf-8") for path in args.source}
    else:
        texts = {name: urllib.request.urlopen(url, timeout=60).read().decode("utf-8") for name, url in SOURCES.items()}
    listed = set().union(*(parse_list(text) for text in texts.values()))

    suffixes, official = public_suffixes(), load_official()
    dropped_suffix = sorted(listed & suffixes)
    dropped_official = sorted(listed & official)
    domains = sorted(listed - suffixes - official)

    bloom = BloomFilter.sized_for(len(domains))
    for domain in domains:
        bloom.add(domain)
    args.out.write_bytes(bloom.to_bytes())

    meta = {
        "sources": {name: SOURCES.get(name, "local file") for name in texts},
        "license": "CC BY-NC-SA 3.0 (ut1), non-commercial",
        "built_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "entries": len(domains), "bits": bloom.m, "hashes": bloom.k,
        "false_positive_rate": FALSE_POSITIVE_RATE,
        "dropped_public_suffixes": dropped_suffix, "dropped_official_domains": len(dropped_official),
    }
    args.meta.write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")

    # Kotlin reads this fixture to prove it computes the same bits: listed domains must
    # hit, and official domains (never inserted) must miss.
    rng = random.Random(20260927)
    misses = [d for d in sorted(official - listed) if d not in bloom]
    rows = [(d, "true") for d in rng.sample(domains, 40)] + [(d, "false") for d in rng.sample(misses, 40)]
    with args.fixture.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["domain", "listed"])
        writer.writerows(rows)

    print(f"wrote {len(domains)} domains ({bloom.m} bits, k={bloom.k}, "
          f"{args.out.stat().st_size} bytes) to {args.out}; dropped {len(dropped_suffix)} suffixes, "
          f"{len(dropped_official)} official domains")


if __name__ == "__main__":
    main()

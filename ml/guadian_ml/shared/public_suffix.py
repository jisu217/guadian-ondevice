"""Registrable-domain lookup over the bundled Public Suffix List.

Same rules as core/judge url/PublicSuffixList.kt: the private section is
included, so `a.vercel.app` and `b.vercel.app` are different sites.
"""

from __future__ import annotations

from functools import lru_cache

from guadian_ml import paths


class PublicSuffixList:
    """Registrable-domain lookup; same rules as core/judge url/PublicSuffixList.kt."""

    def __init__(self, path: str) -> None:
        self.rules, self.wildcards, self.exceptions = set(), set(), set()
        with open(path, encoding="utf-8") as f:
            for raw in f:
                line = raw.strip()
                if not line or line.startswith("//"):
                    continue
                if line.startswith("!"):
                    self.exceptions.add(self._ascii(line[1:]))
                elif line.startswith("*."):
                    self.wildcards.add(self._ascii(line[2:]))
                else:
                    self.rules.add(self._ascii(line))

    @staticmethod
    def _ascii(name: str) -> str:
        try:
            return name.encode("idna").decode("ascii").lower()
        except UnicodeError:
            return name.lower()

    def registrable_domain(self, host: str) -> str | None:
        host = self._ascii(host.strip("."))
        if not host:
            return None
        if ":" in host or all(c.isdigit() or c == "." for c in host):
            return host
        labels = host.split(".")
        for i in range(len(labels)):
            if ".".join(labels[i:]) in self.exceptions:
                return ".".join(labels[i:])
        best = 1
        for i in range(len(labels)):
            length = len(labels) - i
            if length <= best:
                break
            parent = ".".join(labels[i + 1:])
            if ".".join(labels[i:]) in self.rules or (parent and parent in self.wildcards):
                best = length
        return ".".join(labels[-(best + 1):]) if len(labels) > best else None


@lru_cache(maxsize=1)
def default() -> PublicSuffixList:
    """The list in runtime/, loaded once."""
    return PublicSuffixList(str(paths.PUBLIC_SUFFIX_LIST))

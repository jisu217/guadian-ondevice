"""Build runtime/llm_brands.json, the brand data the LLM prompt is built from on both sides.

Sources: the Korean brand table vendored from the original judge
(llm/vendor/judge_agent.py BRANDS) and the global brands, text mentions, host
tokens, account words, and user-content hosts in llm/contract/host_rules.py. The app reads the file
as a resource (core/llm LlmBrands.kt); lists keep their order because order
decides ties.

Usage:
    python -m guadian_ml.resources.llm_brands
"""

from __future__ import annotations

import json
import sys

from guadian_ml import paths
from guadian_ml.llm.contract import host_rules


def export_brands() -> dict:
    return {
        "korean": [{"name": aliases[0], "aliases": list(aliases), "domains": list(domains)}
                   for aliases, domains in host_rules.ja.BRANDS],
        "global_brands": [[token, list(domains)] for token, domains in host_rules.GLOBAL_BRANDS.items()],
        "text_mentions": [[name, token] for name, token in host_rules.TEXT_MENTIONS.items()],
        "host_tokens": [[token, list(domains)] for token, domains in host_rules._BRANDS.items()],
        "account_words": list(host_rules.ACCOUNT_WORDS),
        "user_content_hosts": list(host_rules.USER_CONTENT_HOSTS),
        "min_token_length": host_rules.MIN_TOKEN_LENGTH,
    }


def main() -> int:
    with open(paths.LLM_BRANDS, "w", encoding="utf-8") as f:
        json.dump(export_brands(), f, ensure_ascii=False, indent=1)
        f.write("\n")
    print("wrote", paths.LLM_BRANDS)
    return 0


if __name__ == "__main__":
    sys.exit(main())

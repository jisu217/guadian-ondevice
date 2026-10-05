"""Every location the ML code reads or writes, in one place.

Paths are anchored at the `ml/` directory, so scripts behave the same from
any working directory and a folder move is a one-line change here.
"""

from pathlib import Path

ML_DIR = Path(__file__).resolve().parents[1]
REPO_DIR = ML_DIR.parent

# Files the Android app bundles; each core module ships the ones it reads.
RUNTIME = ML_DIR / "runtime"
PUBLIC_SUFFIX_LIST = RUNTIME / "public_suffix_list.dat"
OFFICIAL_DOMAINS = RUNTIME / "official_domains.txt"
BRAND_DOMAINS = RUNTIME / "brand_domains.tsv"
LLM_BRANDS = RUNTIME / "llm_brands.json"
PAGE_INSPECTION = RUNTIME / "page_inspection.js"
BLOCKLIST = RUNTIME / "blocklist.bloom"
BLOCKLIST_META = RUNTIME / "blocklist.meta.json"

DATA = ML_DIR / "data"
RAW_DATA = DATA / "raw"                 # original GUADIAN gold set (committed)
URL_DATA = DATA / "url"                 # labeled URLs for rule evaluation (committed)
LLM_DATA = DATA / "llm"                 # collected pages, labels, runs (git-ignored: page text)
LLM_SOURCES = LLM_DATA / "sources"      # per source: urls_*, cases_*, labels_* (teacher), ids_addendum_*
LLM_REVIEWS = LLM_DATA / "reviews"      # review_*.csv (human), assistant_review_*.csv, label_overrides.jsonl
LLM_FINAL = LLM_DATA / "final"          # labels_final, splits, sft_* (what training and scoring read)
LLM_EVAL = LLM_DATA / "eval"            # eval_*: model replies and metrics per run
LLM_LOGS = LLM_DATA / "logs"            # output of long collection, labeling, and scoring runs
LLM_CACHE = LLM_DATA / "cache"          # blocklist download, domain-age database
TOKENIZER = LLM_CACHE / "tokenizer"     # local copy of the Qwen3 tokenizer files

# Expected outputs shared with the app's Kotlin tests (training-serving parity).
FIXTURES = ML_DIR / "fixtures"
LLM_FIXTURES = FIXTURES / "llm"
RULE_FIXTURES = FIXTURES / "rules"

REPORTS = ML_DIR / "reports"
LLM_REPORTS = REPORTS / "llm"
PIPELINE_REPORTS = REPORTS / "pipeline"

NOTEBOOKS = ML_DIR / "notebooks"

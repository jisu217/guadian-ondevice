#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

fail() {
  echo "[model-requirements] FAIL: $*" >&2
  exit 1
}

pass() {
  echo "[model-requirements] PASS: $*"
}

require_file() {
  [[ -f "$ROOT_DIR/$1" ]] || fail "missing required file: $1"
}

require_text() {
  local file="$1"
  local pattern="$2"
  local message="$3"

  grep -Eq "$pattern" "$ROOT_DIR/$file" || fail "$message"
}

reject_text() {
  local file="$1"
  local pattern="$2"
  local message="$3"

  if grep -Eiq "$pattern" "$ROOT_DIR/$file"; then
    fail "$message"
  fi
}

require_file ".github/workflows/ci.yml"
require_file "README.md"
require_file "docs/provenance.md"
require_file "docs/technical-report.md"
require_file "ml/README.md"
require_file "ml/pyproject.toml"
require_file "ml/data/url/sample_urls.csv"
require_file "ml/fixtures/rules/rule_parity_cases.csv"
require_file "ml/docs/llm/model-card.md"
require_file "ml/reports/llm/evaluation-report.md"
require_file "ml/reports/pipeline/local-resolution-report.md"
require_file "ml/runtime/brand_domains.tsv"
require_file "ml/runtime/public_suffix_list.dat"
require_file "ml/runtime/llm_brands.json"
pass "required model files exist"

require_text ".github/workflows/ci.yml" "scripts/check-model-requirements\\.sh" \
  "CI must run scripts/check-model-requirements.sh"
pass "CI runs model requirement checks"

# Checks for required *claims*, not exact metric values: a legitimate
# retrain changes the numbers on every run.
require_text "ml/docs/llm/model-card.md" "Forbidden Claims" \
  "LLM model card must define forbidden claims"
require_text "ml/docs/llm/model-card.md" "Known Limits" \
  "LLM model card must state known limits"
pass "model card includes required limits"

require_text "core/judge/src/test/kotlin/com/guadian/ondevice/rules/RuleParityTest.kt" "rule_parity_cases\\.csv" \
  "Kotlin tests must cover rule parity fixture"
require_text "core/judge/src/test/kotlin/com/guadian/ondevice/rules/BlocklistFilterTest.kt" "blocklist_filter_cases\\.csv" \
  "Kotlin tests must cover blocklist filter parity fixture"
require_text "core/llm/src/test/kotlin/com/guadian/ondevice/llm/PromptBuilderParityTest.kt" "fixtures/llm/prompt_cases\\.jsonl" \
  "Kotlin tests must cover LLM prompt parity fixture"
require_text "core/llm/src/test/kotlin/com/guadian/ondevice/llm/PageSignalsParityTest.kt" "fixtures/llm/page_signal_cases\\.json" \
  "Kotlin tests must cover page signal parity fixture"
require_text "core/llm/src/test/kotlin/com/guadian/ondevice/llm/AnswerReaderParityTest.kt" "fixtures/llm/answer_cases\\.jsonl" \
  "Kotlin tests must cover answer reading parity fixture"
require_text "core/llm/src/test/kotlin/com/guadian/ondevice/llm/ChatFormatParityTest.kt" "fixtures/llm/chat_cases\\.json" \
  "Kotlin tests must cover chat template parity fixture"
pass "Python/Kotlin parity checks are wired"

UNSUPPORTED_CLAIM_PATTERN='production[- ]ready|fully production|(guarantee|prove)s?\b.{0,40}\bsafe'
reject_text "README.md" "$UNSUPPORTED_CLAIM_PATTERN" \
  "README contains unsupported production/safety claim"
reject_text "ml/README.md" "$UNSUPPORTED_CLAIM_PATTERN" \
  "model README contains unsupported production/safety claim"
reject_text "docs/technical-report.md" "$UNSUPPORTED_CLAIM_PATTERN" \
  "technical report contains unsupported production/safety claim"
pass "unsupported safety claims are absent from primary docs"

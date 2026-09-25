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
require_file "model/README.md"
require_file "model/docs/model-spec.md"
require_file "model/data/sample_urls.csv"
require_file "model/data/feature_parity_cases.csv"
require_file "model/evaluation/model-card.md"
require_file "model/evaluation/baseline-report.md"
require_file "model/evaluation/trained-model-report.md"
require_file "model/evaluation/train-split.csv"
require_file "model/evaluation/test-split.csv"
require_file "model/artifacts/url_risk_centroid_model.json"
require_file "model/training/train_model.py"
require_file "model/training/test_url_features.py"
require_file "app/src/test/kotlin/com/guardian/ondevice/model/UrlFeatureExtractorTest.kt"
pass "required model files exist"

require_text ".github/workflows/ci.yml" "scripts/check-model-requirements\\.sh" \
  "CI must run scripts/check-model-requirements.sh"
pass "CI runs model requirement checks"

# Checks for required *claims*, not exact metric values: a legitimate
# retrain changes accuracy/sample counts on every run, so hardcoding those
# here would make this script fail on every honest improvement instead of
# catching an actual requirements problem.
require_text "model/evaluation/model-card.md" "not a production security detection service" \
  "model card must state production limitation"
require_text "model/evaluation/model-card.md" "Baseline accuracy on same split" \
  "model card must include baseline comparison"
require_text "model/evaluation/model-card.md" "feature_parity_cases\\.csv" \
  "model card must mention Python/Kotlin parity fixture"
require_text "model/evaluation/model-card.md" "Forbidden Claims" \
  "model card must define forbidden claims"
require_text "model/evaluation/model-card.md" "LLM-consensus labels are treated as prototype evidence" \
  "model card must distinguish LLM-consensus labels from audited truth"
pass "model card includes required limits"

require_text "model/training/test_url_features.py" "feature_parity_cases\\.csv" \
  "Python tests must cover feature parity fixture"
require_text "app/src/test/kotlin/com/guardian/ondevice/model/UrlFeatureExtractorTest.kt" "matches python feature parity fixture" \
  "Kotlin tests must cover feature parity fixture"
pass "Python/Kotlin parity checks are wired"

UNSUPPORTED_CLAIM_PATTERN='production[- ]ready|fully production|(guarantee|prove)s?\b.{0,40}\bsafe'
reject_text "README.md" "$UNSUPPORTED_CLAIM_PATTERN" \
  "README contains unsupported production/safety claim"
reject_text "model/README.md" "$UNSUPPORTED_CLAIM_PATTERN" \
  "model README contains unsupported production/safety claim"
reject_text "docs/technical-report.md" "$UNSUPPORTED_CLAIM_PATTERN" \
  "technical report contains unsupported production/safety claim"
pass "unsupported safety claims are absent from primary docs"

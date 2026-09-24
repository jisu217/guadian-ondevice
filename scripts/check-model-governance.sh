#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

fail() {
  echo "[model-governance] FAIL: $*" >&2
  exit 1
}

pass() {
  echo "[model-governance] PASS: $*"
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
require_file "docs/data-policy.md"
require_file "docs/source-and-reference.md"
require_file "docs/project-report.md"
require_file "model/README.md"
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
require_file "android/app/src/test/kotlin/com/guardian/ondevice/model/UrlFeatureExtractorTest.kt"
pass "required model governance files exist"

require_text ".github/workflows/ci.yml" "scripts/check-model-governance\\.sh" \
  "CI must run scripts/check-model-governance.sh"
pass "CI runs model governance checks"

require_text "model/evaluation/model-card.md" "not a production security detection service" \
  "model card must state production limitation"
require_text "model/evaluation/model-card.md" "Baseline accuracy on same split" \
  "model card must include baseline comparison"
require_text "model/evaluation/model-card.md" "feature_parity_cases\\.csv" \
  "model card must mention Python/Kotlin parity fixture"
require_text "docs/data-policy.md" "Forbidden Claims" \
  "data policy must define forbidden claims"
require_text "docs/data-policy.md" "LLM-consensus labels are treated as prototype evidence" \
  "data policy must distinguish LLM-consensus labels from audited truth"
pass "model card and data policy include required limits"

require_text "model/evaluation/trained-model-report.md" "Train samples: 536" \
  "trained report must reflect current train split"
require_text "model/evaluation/trained-model-report.md" "Test samples: 132" \
  "trained report must reflect current test split"
require_text "model/evaluation/trained-model-report.md" "Model accuracy: 72\\.73%" \
  "trained report must reflect current accuracy"
require_text "model/evaluation/baseline-report.md" "Samples: 668" \
  "baseline report must reflect current dataset size"
require_text "README.md" "Current evaluation with 668 sanitized samples" \
  "README must reflect current dataset size"
pass "headline metrics are synchronized"

require_text "model/training/test_url_features.py" "feature_parity_cases\\.csv" \
  "Python tests must cover feature parity fixture"
require_text "android/app/src/test/kotlin/com/guardian/ondevice/model/UrlFeatureExtractorTest.kt" "matches python feature parity fixture" \
  "Kotlin tests must cover feature parity fixture"
pass "Python/Kotlin parity checks are wired"

reject_text "README.md" "fully production|guarantees? safe|proves? safe" \
  "README contains unsupported production/safety claim"
reject_text "model/README.md" "fully production|guarantees? safe|proves? safe" \
  "model README contains unsupported production/safety claim"
pass "unsupported safety claims are absent from primary docs"

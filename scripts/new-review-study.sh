#!/usr/bin/env bash

set -euo pipefail

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd "$script_dir/.." && pwd)

repo="${REVIEW_STUDY_REPO:-}"
[[ "$repo" != "" ]] || die "set REVIEW_STUDY_REPO=https://github.com/org/repo"

version="${FORMSMITH_VERSION:-v0.1.0-pre.5}"
sha="${FORMSMITH_SHA:-4bd1d7228aebf24a0cc7b80c83c84396ea7d1fbc}"
ownership="${REVIEW_STUDY_OWNERSHIP:-independent}"

case "$ownership" in
  independent|controlled-adopter|maintainer-pressure) ;;
  *) die "REVIEW_STUDY_OWNERSHIP must be independent, controlled-adopter, or maintainer-pressure" ;;
esac

slug="${REVIEW_STUDY_SLUG:-}"
if [[ "$slug" == "" ]]; then
  slug="${repo#https://github.com/}"
  slug="${slug%.git}"
  slug="${slug//\//-}"
fi
[[ "$slug" =~ ^[A-Za-z0-9._-]+$ ]] || die "derived slug is unsafe; set REVIEW_STUDY_SLUG"

today=$(date +%F)
output="${REVIEW_STUDY_OUTPUT:-$repo_root/docs/review-studies/$slug-$today.md}"

if [[ -e "$output" && "${REVIEW_STUDY_FORCE:-0}" != "1" ]]; then
  die "output exists; set REVIEW_STUDY_FORCE=1 to overwrite: $output"
fi

baseline="${REVIEW_STUDY_BASELINE:-<sha>}"
range="${REVIEW_STUDY_RANGE:-$repo/pull/000}"
target_paths="${REVIEW_STUDY_TARGET_PATHS:-src test}"
ordinary_gates="${REVIEW_STUDY_ORDINARY_GATES:-formatter, clj-kondo, tests, build}"
formsmith_gate="${REVIEW_STUDY_FORMSMITH_GATE:-clojure -M:formsmith check src test}"
applied_mode="${REVIEW_STUDY_APPLIED_MODE:-default}"
ignored_paths="${REVIEW_STUDY_IGNORED_PATHS:-target, generated assets, vendored code}"

adoption_pr="${REVIEW_STUDY_ADOPTION_PR:-$repo/pull/000}"
pr_ci="${REVIEW_STUDY_PR_CI:-$repo/actions/runs/000}"
main_ci="${REVIEW_STUDY_MAIN_CI:-$repo/actions/runs/000}"
review_before="${REVIEW_STUDY_REVIEW_BEFORE:-$repo/pull/000}"
review_after="${REVIEW_STUDY_REVIEW_AFTER:-$repo/pull/000}"

before_check="${REVIEW_STUDY_BEFORE_CHECK:-files=0 changed=0 findings=0}"
after_check="${REVIEW_STUDY_AFTER_CHECK:-files=0 changed=0 findings=0}"
aggressive_preview="${REVIEW_STUDY_AGGRESSIVE_PREVIEW:-files=0 changed=0 findings=0}"
contracts="${REVIEW_STUDY_CONTRACTS:-0}"

eliminated="${REVIEW_STUDY_ELIMINATED:-0}"
missed="${REVIEW_STUDY_MISSED:-0}"
false_positive="${REVIEW_STUDY_FALSE_POSITIVE:-0}"
accepted_suppression="${REVIEW_STUDY_ACCEPTED_SUPPRESSION:-0}"
out_of_scope="${REVIEW_STUDY_OUT_OF_SCOPE:-0}"
unclear="${REVIEW_STUDY_UNCLEAR:-0}"

for count in "$eliminated" "$missed" "$false_positive" "$accepted_suppression" "$out_of_scope" "$unclear"; do
  [[ "$count" =~ ^[0-9]+$ ]] || die "classification counts must be numeric"
done

mkdir -p "$(dirname "$output")"

cat > "$output" <<EOF
# Review Study: $slug

## Repository

- repository: <$repo>
- ownership: $ownership
- baseline commit: \`$baseline\`
- measured commit or PR range: <$range>
- Formsmith version: \`$version\`
- Formsmith SHA: \`$sha\`

## Study Boundary

- target paths: \`$target_paths\`
- ordinary gates: $ordinary_gates
- Formsmith gate: \`$formsmith_gate\`
- applied mode: $applied_mode
- ignored paths: $ignored_paths

## Public Evidence

- adoption PR: <$adoption_pr>
- PR CI: <$pr_ci>
- main CI after merge: <$main_ci>
- review sample before Formsmith: <$review_before>
- review sample after Formsmith: <$review_after>

## Finding Summary

\`\`\`text
before_check=$before_check
after_check=$after_check
aggressive_preview=$aggressive_preview
contracts=$contracts
\`\`\`

## Review Comment Classification

| Classification | Count | Notes |
| --- | ---: | --- |
| eliminated-by-formsmith | $eliminated | TODO |
| missed-covered-rule | $missed | TODO |
| false-positive | $false_positive | TODO |
| accepted-suppression | $accepted_suppression | TODO |
| out-of-scope | $out_of_scope | TODO |
| unclear | $unclear | TODO |

## Suppressions And False Positives

TODO: list every suppression or false positive with file, rule id, reason, and
public discussion link, or state \`none\`.

## Failures

TODO: list semantic regressions, rejected default fixes, parser crashes, CI
failures, or state \`none\`.

## Verdict

TODO: state whether this packet counts as maintainer-pressure,
controlled-adopter, independent-adoption, or does not count. Do not call the
result L5 unless it is part of a larger multi-repo public study that meets
[\`docs/review-elimination-study.md\`](../review-elimination-study.md).
EOF

FORMSMITH_REVIEW_STUDY_ALLOW_PLACEHOLDERS=1 \
  bash "$repo_root/scripts/verify-review-study.sh" "$output"

printf 'review_study_scaffold=%s\n' "$output"

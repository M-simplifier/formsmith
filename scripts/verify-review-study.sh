#!/usr/bin/env bash

set -euo pipefail

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

study="${1:-}"

[[ "$study" != "" ]] || die "usage: scripts/verify-review-study.sh <study.md>"
[[ -s "$study" ]] || die "study file does not exist or is empty: $study"

command -v rg >/dev/null 2>&1 || die "verify-review-study requires rg"

for heading in \
  "## Repository" \
  "## Study Boundary" \
  "## Public Evidence" \
  "## Finding Summary" \
  "## Review Comment Classification" \
  "## Suppressions And False Positives" \
  "## Failures" \
  "## Verdict"
do
  rg -Fq "$heading" "$study" || die "missing required heading: $heading"
done

for token in \
  "repository:" \
  "ownership:" \
  "baseline commit:" \
  "Formsmith version:" \
  "Formsmith SHA:" \
  "target paths:" \
  "ordinary gates:" \
  "Formsmith gate:" \
  "applied mode:"
do
  rg -Fq "$token" "$study" || die "missing required field: $token"
done

for classification in \
  "eliminated-by-formsmith" \
  "missed-covered-rule" \
  "false-positive" \
  "accepted-suppression" \
  "out-of-scope" \
  "unclear"
do
  rg -Fq "$classification" "$study" || die "missing classification row: $classification"
done

rg -q -e 'https://github\.com/[^ )>]+' "$study" ||
  die "study must include at least one public GitHub evidence URL"

rg -q -e 'ownership: (independent|controlled-adopter|maintainer-pressure)' "$study" ||
  die "ownership must be one of independent, controlled-adopter, maintainer-pressure"

if [[ "${FORMSMITH_REVIEW_STUDY_ALLOW_PLACEHOLDERS:-0}" != "1" ]] &&
   rg -n '<repository>|<sha>|<record>|org/repo|pull/000|runs/000|TODO|TBD' "$study"; then
  die "study still contains template placeholders"
fi

printf 'review_study_verifier=pass file=%s\n' "$study"

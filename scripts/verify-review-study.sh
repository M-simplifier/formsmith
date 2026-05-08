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

github_verify="${FORMSMITH_REVIEW_STUDY_VERIFY_GITHUB:-0}"

trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "$value"
}

classification_count() {
  local label="$1"
  awk -F '|' -v label="$label" '
    function trim(s) {
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", s)
      return s
    }
    trim($2) == label {
      print trim($3)
      found = 1
      exit
    }
    END {
      if (!found) {
        exit 1
      }
    }
  ' "$study"
}

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

ownership=$(sed -n 's/^- ownership: //p' "$study" | head -n 1)
url_count=$(rg -o -e 'https://github\.com/[^ )>]+' "$study" | wc -l | tr -d ' ')
actions_count=$(rg -o -e 'https://github\.com/[^/]+/[^/]+/actions/runs/[0-9]+' "$study" | wc -l | tr -d ' ')

if [[ "${FORMSMITH_REVIEW_STUDY_ALLOW_PLACEHOLDERS:-0}" != "1" ]]; then
  [[ "$url_count" -ge 3 ]] ||
    die "completed study must include at least three public GitHub evidence URLs"
  [[ "$actions_count" -ge 1 ]] ||
    die "completed study must include at least one GitHub Actions run URL"
fi

if [[ "${FORMSMITH_REVIEW_STUDY_ALLOW_PLACEHOLDERS:-0}" != "1" ]] &&
   rg -n '<repository>|<sha>|<record>|org/repo|pull/000|runs/000|TODO|TBD' "$study"; then
  die "study still contains template placeholders"
fi

if ! rg -q -e '^before_check=' "$study"; then
  die "missing before_check finding summary"
fi

if ! rg -q -e '^after_check=files=[0-9]+ changed=[0-9]+ findings=[0-9]+' "$study"; then
  die "after_check must include files, changed, and findings counts"
fi

if rg -q -e '^contracts=not recorded' "$study"; then
  die "contracts output must be recorded, even when it is contracts=0"
fi

eliminated=$(classification_count "eliminated-by-formsmith") ||
  die "cannot parse eliminated-by-formsmith count"
missed=$(classification_count "missed-covered-rule") ||
  die "cannot parse missed-covered-rule count"
false_positive=$(classification_count "false-positive") ||
  die "cannot parse false-positive count"
accepted_suppression=$(classification_count "accepted-suppression") ||
  die "cannot parse accepted-suppression count"
out_of_scope=$(classification_count "out-of-scope") ||
  die "cannot parse out-of-scope count"
unclear=$(classification_count "unclear") ||
  die "cannot parse unclear count"

for count in "$eliminated" "$missed" "$false_positive" "$accepted_suppression" "$out_of_scope" "$unclear"; do
  [[ "$count" =~ ^[0-9]+$ ]] || die "classification counts must be numeric"
done

verdict_text=$(awk '
  /^## Verdict/ {in_verdict = 1; next}
  /^## / {in_verdict = 0}
  in_verdict {print}
' "$study")

if [[ "$eliminated" -eq 0 ]] &&
   printf '%s\n' "$verdict_text" | rg -iq -e 'counts as.*L5|supports.*L5|L5 evidence|is L5'; then
  die "study cannot claim L5-style evidence with zero eliminated-by-formsmith comments"
fi

if [[ "$ownership" == "independent" ]] &&
   rg -q -e 'review sample before Formsmith: unavailable|review sample after Formsmith: unavailable' "$study"; then
  die "independent studies must include before and after public review samples"
fi

if [[ "$github_verify" == "1" ]]; then
  command -v gh >/dev/null 2>&1 ||
    die "FORMSMITH_REVIEW_STUDY_VERIFY_GITHUB=1 requires gh"

  while read -r url; do
    [[ "$url" != "" ]] || continue
    path="${url#https://github.com/}"
    IFS='/' read -r owner repo _ _ run_id <<<"$path"
    status=$(gh run view "$run_id" --repo "$owner/$repo" --json status,conclusion --jq '.status + " " + (.conclusion // "")')
    [[ "$status" == "completed success" ]] ||
      die "GitHub Actions run is not completed success: $url ($status)"
  done < <(rg -o -e 'https://github\.com/[^/]+/[^/]+/actions/runs/[0-9]+' "$study")
fi

printf 'review_study_verifier=pass file=%s ownership=%s urls=%s action_runs=%s eliminated=%s missed=%s false_positive=%s suppressions=%s out_of_scope=%s unclear=%s\n' \
  "$study" "$ownership" "$url_count" "$actions_count" "$eliminated" "$missed" "$false_positive" "$accepted_suppression" "$out_of_scope" "$unclear"

#!/usr/bin/env bash

set -euo pipefail

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

report_dir="${1:-target/field-trials/reports}"
out_dir="${2:-$(dirname "$report_dir")}"

[[ -d "$report_dir" ]] || die "report directory does not exist: $report_dir"

mkdir -p "$out_dir"

summary_file="$out_dir/summary.tsv"
repo_rule_file="$out_dir/repo-rule-summary.tsv"
rule_file="$out_dir/rule-summary.tsv"
rule_tmp=$(mktemp "${TMPDIR:-/tmp}/formsmith-rule-summary.XXXXXX")
cleanup() {
  rm -f "$rule_tmp"
}
trap cleanup EXIT

printf 'repo\tfiles\tchanged\tfindings\treport\n' >"$summary_file"
printf 'repo\trule\tfindings\n' >"$repo_rule_file"

for report in "$report_dir"/*.txt; do
  [[ -e "$report" ]] || die "no report files found in $report_dir"

  repo=$(basename "$report" .txt)
  summary=$(tail -n 1 "$report")

  files=$(printf '%s\n' "$summary" | sed -n 's/.*files=\([0-9][0-9]*\).*/\1/p')
  changed=$(printf '%s\n' "$summary" | sed -n 's/.*changed=\([0-9][0-9]*\).*/\1/p')
  findings=$(printf '%s\n' "$summary" | sed -n 's/.*findings=\([0-9][0-9]*\).*/\1/p')

  [[ "$files" != "" ]] || die "cannot parse files count from $report"
  [[ "$changed" != "" ]] || die "cannot parse changed count from $report"
  [[ "$findings" != "" ]] || die "cannot parse findings count from $report"

  printf '%s\t%s\t%s\t%s\t%s\n' "$repo" "$files" "$changed" "$findings" "$report" >>"$summary_file"

  sed -n 's/.*\[\([^]]*\)\].*/\1/p' "$report" |
    sort |
    uniq -c |
    while read -r count rule; do
      printf '%s\t%s\t%s\n' "$repo" "$rule" "$count" >>"$repo_rule_file"
    done
done

awk -F '\t' '
  NR > 1 {
    findings[$2] += $3
    seen[$2 SUBSEP $1] = 1
  }
  END {
    for (rule in findings) {
      repo_count = 0
      for (key in seen) {
        split(key, parts, SUBSEP)
        if (parts[1] == rule) {
          repo_count += 1
        }
      }
      print rule "\t" repo_count "\t" findings[rule]
    }
  }
' "$repo_rule_file" >"$rule_tmp"

{
  printf 'rule\trepos\tfindings\n'
  sort "$rule_tmp"
} >"$rule_file"

total_files=$(awk -F '\t' 'NR > 1 {sum += $2} END {print sum + 0}' "$summary_file")
total_changed=$(awk -F '\t' 'NR > 1 {sum += $3} END {print sum + 0}' "$summary_file")
total_findings=$(awk -F '\t' 'NR > 1 {sum += $4} END {print sum + 0}' "$summary_file")
repo_count=$(awk 'END {print NR - 1}' "$summary_file")
rule_count=$(awk 'END {print NR - 1}' "$rule_file")

printf 'field_trial_summary repos=%s files=%s changed=%s findings=%s rules=%s summary=%s rules=%s\n' \
  "$repo_count" "$total_files" "$total_changed" "$total_findings" "$rule_count" "$summary_file" "$rule_file"

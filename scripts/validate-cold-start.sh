#!/usr/bin/env bash

set -euo pipefail

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

require_text() {
  local file="$1"
  local pattern="$2"
  local label="$3"

  if ! grep -Fq "$pattern" "$file"; then
    printf 'missing expected output for %s\n' "$label" >&2
    printf 'expected: %s\n' "$pattern" >&2
    printf 'actual output:\n' >&2
    cat "$file" >&2
    exit 1
  fi
}

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd "$script_dir/.." && pwd)

managed_root=1
scratch_root="${COLD_START_VALIDATION_ROOT:-}"

if [[ -n "$scratch_root" ]]; then
  managed_root=0
  rm -rf "$scratch_root"
  mkdir -p "$scratch_root"
else
  scratch_root=$(mktemp -d "${TMPDIR:-/tmp}/formsmith-cold-start.XXXXXX")
fi

fresh_repo="$scratch_root/formsmith"
log_dir="$scratch_root/logs"

cleanup() {
  if [[ "$managed_root" -eq 1 && "${KEEP_COLD_START_VALIDATION:-0}" != "1" ]]; then
    rm -rf "$scratch_root"
  else
    printf 'Kept cold-start validation workspace: %s\n' "$scratch_root" >&2
  fi
}

trap cleanup EXIT

mkdir -p "$fresh_repo" "$log_dir"

rsync -a \
  --exclude='.git/' \
  --exclude='playgrounds/' \
  --exclude='target/' \
  --exclude='.cpcache/' \
  --exclude='.clj-kondo/.cache/' \
  --exclude='.DS_Store' \
  "$repo_root/" "$fresh_repo/"

run_and_capture() {
  local name="$1"
  shift
  local log_file="$log_dir/$name.log"

  (
    cd "$fresh_repo"
    "$@"
  ) | tee "$log_file"
}

run_and_capture prepare bash scripts/cold-start-demo.sh prepare
run_and_capture lint bash scripts/cold-start-demo.sh lint
run_and_capture check bash scripts/cold-start-demo.sh check
run_and_capture preview bash scripts/cold-start-demo.sh preview
run_and_capture aggressive-preview bash scripts/cold-start-demo.sh aggressive-preview

require_text "$log_dir/prepare.log" "Prepared cold-start demo workspace: $fresh_repo/playgrounds/cold-start-demo" "prepare"
require_text "$log_dir/lint.log" "files=3 changed=0 findings=9" "lint summary"
require_text "$log_dir/lint.log" "[if/not-condition]" "lint signal"
require_text "$log_dir/check.log" "files=3 changed=2 findings=7" "check summary"
require_text "$log_dir/check.log" "[let/nested-let]" "check signal"
require_text "$log_dir/preview.log" "files=3 changed=2 findings=7" "preview summary"
require_text "$log_dir/preview.log" "[let/nested-let]" "preview signal"
require_text "$log_dir/aggressive-preview.log" "files=1 changed=1 findings=4" "aggressive preview summary"
require_text "$log_dir/aggressive-preview.log" "[condition/and-seq-not-empty]" "aggressive preview signal"

printf 'cold-start validation passed\n'
printf 'fresh_repo=%s\n' "$fresh_repo"
printf 'lint_summary=files=3 changed=0 findings=9\n'
printf 'check_summary=files=3 changed=2 findings=7\n'
printf 'preview_summary=files=3 changed=2 findings=7\n'
printf 'aggressive_preview_summary=files=1 changed=1 findings=4\n'

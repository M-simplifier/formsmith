#!/usr/bin/env bash

set -euo pipefail

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd "$script_dir/.." && pwd)

command -v git >/dev/null 2>&1 || die "git is required"
command -v clojure >/dev/null 2>&1 || die "Clojure CLI is required"

scratch="${FORMSMITH_FIELD_TRIAL_DIR:-$repo_root/target/field-trials}"
work_dir="$scratch/work"
report_dir="$scratch/reports"

rm -rf "$work_dir" "$report_dir"
mkdir -p "$work_dir" "$report_dir"

run_trial() {
  local id="$1"
  local url="$2"
  local sha="$3"
  shift 3
  local rel_paths=("$@")
  local clone_dir="$work_dir/$id"
  local report="$report_dir/$id.txt"

  git clone --quiet --depth 1 "$url" "$clone_dir"
  git -C "$clone_dir" fetch --quiet --depth 1 origin "$sha"
  git -C "$clone_dir" checkout --quiet "$sha"

  local paths=()
  local rel
  for rel in "${rel_paths[@]}"; do
    if [[ -e "$clone_dir/$rel" ]]; then
      paths+=("$clone_dir/$rel")
    fi
  done

  [[ "${#paths[@]}" -gt 0 ]] || die "trial $id has no existing paths"

  set +e
  local output
  output=$(cd "$repo_root" &&
    clojure -M -m formsmith.main fix --check --rewrite-only "${paths[@]}" 2>&1)
  local status=$?
  set -e

  printf '%s\n' "$output" >"$report"

  if [[ "$status" != "0" && "$status" != "2" ]]; then
    printf '%s\n' "$output" >&2
    die "trial $id failed with status $status"
  fi

  local summary
  summary=$(printf '%s\n' "$output" | tail -n 1)
  printf 'field_trial id=%s status=%s %s report=%s\n' \
    "$id" "$status" "$summary" "$report"
}

run_trial "reitit" \
  "https://github.com/metosin/reitit.git" \
  "106fc4c7a09290c8e2df2d4ef9570ea1322ab2ab" \
  modules examples test

run_trial "malli" \
  "https://github.com/metosin/malli.git" \
  "a74e3b45efa30b3bcdb2e997f337c71614eba3c5" \
  src test app

run_trial "kaocha" \
  "https://github.com/lambdaisland/kaocha.git" \
  "8846f91c9bf4338c561ffb866b5a8890e22889cd" \
  src test examples

run_trial "re-frame" \
  "https://github.com/day8/re-frame.git" \
  "1a1bf1df6570b17a148ebce70d500ec2da393cdc" \
  src test examples

run_trial "re-frame-10x" \
  "https://github.com/day8/re-frame-10x.git" \
  "a3c309430d9e24456b4760f125133abbffc9bdfa" \
  src test examples

run_trial "conduit" \
  "https://github.com/jacekschae/conduit.git" \
  "ae3c15df1b76d3e0157e32ae24bae52bdb7ea365" \
  src test

run_trial "pingcrm-clojure" \
  "https://github.com/prestancedesign/pingcrm-clojure.git" \
  "3ec40f5cf018fe1485debba94a5ebb70ea1a0e04" \
  src dev

run_trial "usermanager-reitit-example" \
  "https://github.com/prestancedesign/usermanager-reitit-example.git" \
  "887df38f6635083e2e705cdad010c59231bab37f" \
  src dev

bash "$script_dir/summarize-field-trial.sh" "$report_dir" "$scratch"

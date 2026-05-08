#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
NEXTMOON_DIR="${NEXTMOON_DIR:-$ROOT_DIR/../nextmoon}"
DIRTY_CLOJURE_DIR="${DIRTY_CLOJURE_DIR:-$ROOT_DIR/../dirty-clojure}"

require_dir() {
  if [[ ! -d "$1" ]]; then
    printf 'Missing benchmark repo: %s\n' "$1" >&2
    printf 'This is a maintainer evidence packet, not the repo-only first-run path.\n' >&2
    printf 'Clone the benchmark repos next to formsmith or set NEXTMOON_DIR and DIRTY_CLOJURE_DIR.\n' >&2
    printf 'For the self-contained public demo, run: bb validate-cold-start\n' >&2
    exit 1
  fi
}

run_section() {
  local label="$1"
  shift

  printf '\n[%s]\n' "$label"
  printf '$'
  for arg in "$@"; do
    printf ' %q' "$arg"
  done
  printf '\n'
  local status=0
  if "$@"; then
    status=0
  else
    status=$?
  fi

  if [[ "$status" -ne 0 && "$status" -ne 2 ]]; then
    return "$status"
  fi

  if [[ "$status" -eq 2 ]]; then
    printf '(findings present; continuing)\n'
  fi
}

require_dir "$NEXTMOON_DIR"
require_dir "$DIRTY_CLOJURE_DIR"

cd "$ROOT_DIR"

run_section "nextmoon pure signal" \
  clojure -M -m formsmith.main lint --no-kondo "$NEXTMOON_DIR/src"

run_section "dirty-clojure pure signal" \
  clojure -M -m formsmith.main lint --no-kondo "$DIRTY_CLOJURE_DIR/src" "$DIRTY_CLOJURE_DIR/test"

run_section "nextmoon safe preview" \
  clojure -M -m formsmith.main fix --check --rewrite-only "$NEXTMOON_DIR/src"

run_section "dirty-clojure safe preview" \
  clojure -M -m formsmith.main fix --check --rewrite-only "$DIRTY_CLOJURE_DIR/src" "$DIRTY_CLOJURE_DIR/test"

run_section "dirty-clojure cljs aggressive preview" \
  clojure -M -m formsmith.main fix --check --aggressive --rewrite-only "$DIRTY_CLOJURE_DIR/src/cljs/dirty_clojure/frontend/core.cljs"

run_section "nextmoon cljs aggressive preview" \
  clojure -M -m formsmith.main fix --check --aggressive --rewrite-only "$NEXTMOON_DIR/src/nextmoon/ui/core.cljs"

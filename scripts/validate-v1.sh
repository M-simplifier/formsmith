#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
NEXTMOON_DIR="${NEXTMOON_DIR:-$ROOT_DIR/../nextmoon}"
DIRTY_CLOJURE_DIR="${DIRTY_CLOJURE_DIR:-$ROOT_DIR/../dirty-clojure}"
KONDO_VERSION="${KONDO_VERSION:-2026.01.19}"
CLJFMT_VERSION="${CLJFMT_VERSION:-0.16.3}"

failures=0

require_dir() {
  if [[ ! -d "$1" ]]; then
    printf 'Missing validation repo: %s\n' "$1" >&2
    printf 'This is a maintainer evidence packet, not the repo-only first-run path.\n' >&2
    printf 'Clone the benchmark repos next to formsmith or set NEXTMOON_DIR and DIRTY_CLOJURE_DIR.\n' >&2
    printf 'For the self-contained public demo, run: bb validate-cold-start\n' >&2
    exit 1
  fi
}

run_gate() {
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

  if [[ "$status" -eq 0 ]]; then
    printf 'status=pass\n'
    return 0
  fi

  printf 'status=fail exit=%s\n' "$status"
  failures=$((failures + 1))
  return 0
}

require_dir "$NEXTMOON_DIR"
require_dir "$DIRTY_CLOJURE_DIR"

run_gate "dirty-clojure tests" \
  env bash -lc "cd $(printf '%q' "$DIRTY_CLOJURE_DIR") && npm test"

run_gate "dirty-clojure lint" \
  env bash -lc "cd $(printf '%q' "$DIRTY_CLOJURE_DIR") && npm run lint"

run_gate "dirty-clojure format check" \
  env bash -lc "cd $(printf '%q' "$DIRTY_CLOJURE_DIR") && npm run format:check"

run_gate "nextmoon tests" \
  env bash -lc "cd $(printf '%q' "$NEXTMOON_DIR") && clojure -M:test"

run_gate "nextmoon ad hoc clj-kondo" \
  env bash -lc "cd $(printf '%q' "$NEXTMOON_DIR") && clojure -Sdeps '{:deps {clj-kondo/clj-kondo {:mvn/version \"$KONDO_VERSION\"}}}' -M -m clj-kondo.main --lint src test deps.edn shadow-cljs.edn"

run_gate "nextmoon ad hoc cljfmt check" \
  env bash -lc "cd $(printf '%q' "$NEXTMOON_DIR") && clojure -Sdeps '{:deps {dev.weavejester/cljfmt {:mvn/version \"$CLJFMT_VERSION\"}}}' -M -m cljfmt.main check src test deps.edn shadow-cljs.edn"

run_gate "formsmith benchmark loop" \
  env bash -lc "cd $(printf '%q' "$ROOT_DIR") && bb benchmark-v1"

printf '\n[summary]\n'
if [[ "$failures" -eq 0 ]]; then
  printf 'validation=pass failures=0\n'
  exit 0
fi

printf 'validation=fail failures=%s\n' "$failures"
exit 1

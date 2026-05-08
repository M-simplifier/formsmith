#!/usr/bin/env bash

set -euo pipefail

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage:
  scripts/verify-adoption.sh <repo-path> <expected-tag> <expected-sha>

Checks that a target repository has the public Formsmith adoption shape:

- deps.edn contains the public formsmith git dependency
- deps.edn pins the expected tag and sha
- .formsmith.edn exists
- a CI workflow exists
- either CI directly runs clojure -M:formsmith check or bb ci depends on a formsmith check task

Set FORMSMITH_ADOPTION_RUN_CHECK=1 to also run:
  clojure -M:formsmith check src test
EOF
}

repo_path="${1:-}"
expected_tag="${2:-}"
expected_sha="${3:-}"

[[ "$repo_path" != "" ]] || { usage >&2; exit 1; }
[[ "$expected_tag" != "" ]] || { usage >&2; exit 1; }
[[ "$expected_sha" != "" ]] || { usage >&2; exit 1; }
[[ -d "$repo_path" ]] || die "repo path does not exist: $repo_path"

cd "$repo_path"

[[ -s deps.edn ]] || die "missing deps.edn"
[[ -s .formsmith.edn ]] || die "missing .formsmith.edn"
[[ -d .github/workflows ]] || die "missing .github/workflows"

rg -Fq 'io.github.M-simplifier/formsmith' deps.edn ||
  die "deps.edn does not contain the public formsmith dependency"
rg -Fq ":git/tag \"$expected_tag\"" deps.edn ||
  die "deps.edn does not pin expected tag: $expected_tag"
rg -Fq ":git/sha \"$expected_sha\"" deps.edn ||
  die "deps.edn does not pin expected sha: $expected_sha"
rg -Fq ':formsmith' deps.edn ||
  die "deps.edn does not define a :formsmith alias"

if rg -Fq 'clojure -M:formsmith check' .github/workflows bb.edn 2>/dev/null; then
  :
elif rg -Fq 'bb ci' .github/workflows 2>/dev/null &&
     rg -Fq 'clojure -M:formsmith check' bb.edn 2>/dev/null; then
  :
else
  die "CI does not visibly run formsmith check"
fi

if [[ "${FORMSMITH_ADOPTION_RUN_CHECK:-0}" == "1" ]]; then
  [[ -d src ]] || die "cannot run check: missing src directory"
  paths=(src)
  [[ -d test ]] && paths+=(test)
  clojure -M:formsmith check "${paths[@]}"
fi

printf 'adoption_verifier=pass repo=%s tag=%s sha=%s\n' "$repo_path" "$expected_tag" "$expected_sha"

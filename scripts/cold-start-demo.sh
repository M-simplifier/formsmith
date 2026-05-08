#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/cold-start-demo.sh <command>

Commands:
  prepare             Create or reset the repo-only demo workspace
  path                Print the demo workspace path
  lint                Run pure formsmith lint on the demo workspace
  check               Run canonical no-write check on the demo workspace
  preview             Run safe no-write preview on the demo workspace
  fix                 Apply safe rewrites to the demo workspace
  aggressive-preview  Run aggressive preview on the demo cljs file
EOF
}

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd "$script_dir/.." && pwd)
source_dir="$repo_root/examples/cold-start-demo"
demo_dir="${COLD_START_DEMO_DIR:-$repo_root/playgrounds/cold-start-demo}"
demo_frontend_file="$demo_dir/src/cljs/cold_start_demo/ui.cljs"

ensure_demo_dir() {
  [ -d "$demo_dir" ] || die "cold-start demo workspace missing: $demo_dir. Run 'scripts/cold-start-demo.sh prepare' first."
}

run_formsmith() {
  local status=0
  if "$@"; then
    status=0
  else
    status=$?
  fi

  if [[ "$status" -ne 0 && "$status" -ne 2 ]]; then
    return "$status"
  fi
}

prepare_demo() {
  [ -d "$source_dir" ] || die "demo source missing: $source_dir"

  rm -rf "$demo_dir"
  mkdir -p "$demo_dir"
  rsync -a "$source_dir/" "$demo_dir/"

  cat > "$demo_dir/TRY_FORMSMITH.md" <<EOF
# Try formsmith

This is a disposable demo workspace copied from:

- \`$source_dir\`

Run from the \`formsmith\` repo:

\`\`\`bash
bash scripts/cold-start-demo.sh lint
bash scripts/cold-start-demo.sh preview
bash scripts/cold-start-demo.sh fix
\`\`\`

Reset the workspace any time with:

\`\`\`bash
bash scripts/cold-start-demo.sh prepare
\`\`\`
EOF

  printf 'Prepared cold-start demo workspace: %s\n' "$demo_dir"
}

run_lint() {
  ensure_demo_dir
  cd "$repo_root"
  run_formsmith clojure -M -m formsmith.main lint --no-config --no-kondo "$demo_dir/src" "$demo_dir/test"
}

run_preview() {
  ensure_demo_dir
  cd "$repo_root"
  run_formsmith clojure -M -m formsmith.main fix --no-config --check --rewrite-only "$demo_dir/src" "$demo_dir/test"
}

run_check() {
  ensure_demo_dir
  cd "$repo_root"
  run_formsmith clojure -M -m formsmith.main check --no-config "$demo_dir/src" "$demo_dir/test"
}

run_fix() {
  ensure_demo_dir
  cd "$repo_root"
  clojure -M -m formsmith.main fix --no-config "$demo_dir/src" "$demo_dir/test"
}

run_aggressive_preview() {
  ensure_demo_dir
  [ -f "$demo_frontend_file" ] || die "demo cljs file missing: $demo_frontend_file"
  cd "$repo_root"
  run_formsmith clojure -M -m formsmith.main fix --no-config --check --aggressive --rewrite-only "$demo_frontend_file"
}

command="${1:-}"

case "$command" in
  prepare)
    prepare_demo
    ;;
  path)
    printf '%s\n' "$demo_dir"
    ;;
  lint)
    run_lint
    ;;
  check)
    run_check
    ;;
  preview)
    run_preview
    ;;
  fix)
    run_fix
    ;;
  aggressive-preview)
    run_aggressive_preview
    ;;
  *)
    usage
    exit 1
    ;;
esac

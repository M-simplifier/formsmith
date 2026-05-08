#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
PROOF_PATH="proofs/Formsmith/Core.lean"

cd "$ROOT_DIR"

while IFS= read -r proof_path; do
  if command -v lean >/dev/null 2>&1; then
    lean "$proof_path"
  elif command -v nix >/dev/null 2>&1; then
    nix shell nixpkgs#lean4 --command lean "$proof_path"
  else
    printf 'error: Lean is not installed and nix is unavailable\n' >&2
    exit 1
  fi
done < <(find proofs -name '*.lean' | sort)

printf 'proofs=pass file=%s\n' "$PROOF_PATH"

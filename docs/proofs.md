# Proofs

`formsmith` distinguishes between ordinary canonical fixes and formally
certified fixes.

The current proof surface is intentionally small. It does not claim a full
Clojure semantics. It models only the truthiness behavior needed for the first
certified rewrite family:

- `when` with a negated test can be written as `when-not`
- `if` with a negated test can be written as `if-not`

The Lean artifact lives at:

```text
proofs/Formsmith/Core.lean
```

Verify it with:

```bash
bb verify-proofs
```

The verifier uses a local `lean` command when available, and falls back to
`nix shell nixpkgs#lean4` when Nix is available.

## Claim Boundary

`certified-fix` means the finding has a known proof artifact in the current
modeled semantics and that `formsmith` checked the implementation-side side
conditions needed to use that proof.

For the first certified family, that means the source operators are known to be
`clojure.core` forms either because they are fully qualified or because analyzer
facts resolve them to `clojure.core`. Certified output uses fully qualified
`clojure.core/if-not` and `clojure.core/when-not` so local bindings cannot
change the replacement's meaning.

It does not mean every Clojure program is fully modeled.

Rules can move through this ladder:

1. `standard-canonical-fix`
2. `analyzer-guarded-fix`
3. `certified-fix`

The proof model should grow only when a rule family needs the new semantic
surface.

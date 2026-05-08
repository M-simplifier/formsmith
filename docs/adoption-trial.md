# Adoption Trial Protocol

## Purpose

This protocol defines the evidence needed before `formsmith` can claim more
than maintainer-controlled pressure testing.

The trial question is:

> can a public Clojure or ClojureScript project adopt a released Formsmith
> coordinate, enforce it in CI, and report before/after review signal without
> maintainer help?

## Trial Boundary

A valid adoption trial must be public and reproducible from public artifacts.

Required public evidence:

- repository URL
- pull request URL that introduces Formsmith
- CI run URL for that pull request
- CI run URL after merge to `main`
- exact Formsmith release tag and SHA
- `.formsmith.edn`
- `deps.edn` with a `:formsmith` alias
- CI gate that runs `formsmith check`
- before/after finding summary
- false-positive or suppression notes

Do not count private repos, local-only trials, or undocumented maintainer
experiments as L4 evidence.

## Cold Adopter Steps

1. Add the released Formsmith git dependency to `deps.edn`.
2. Add `.formsmith.edn`.
3. Run:

```bash
clojure -M:formsmith check src test
clojure -M:formsmith fix --check --aggressive src test
```

4. If the existing repo has many findings, generate a baseline:

```bash
clojure -M:formsmith baseline src test -o .formsmith-baseline.edn
```

5. Add Formsmith to CI.
6. Open a public PR.
7. Record the PR, CI, findings, and suppression decisions under
   `docs/adoptions/`.

## Structural Verifier

Run this from the Formsmith repo against a local checkout of the adopter repo:

```bash
scripts/verify-adoption.sh ../adopter-repo v0.1.0-pre.2 9b1b4fdca1a045ef79495ce7e94106b570f00368
```

To also run the target repo's Formsmith check:

```bash
FORMSMITH_ADOPTION_RUN_CHECK=1 scripts/verify-adoption.sh ../adopter-repo v0.1.0-pre.2 9b1b4fdca1a045ef79495ce7e94106b570f00368
```

The verifier is not a maturity proof by itself. It only checks that the public
adoption shape is present.

## Rating Use

- One maintainer-built pressure app is L3 evidence.
- One clean, cold public adoption outside the Formsmith repo is the minimum L4
  candidate evidence.
- Several independent public adoptions with low false-positive rates are needed
  before L5 can be argued.

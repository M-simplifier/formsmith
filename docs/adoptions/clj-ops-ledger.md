# clj-ops-ledger Adoption Record

## Repository

<https://github.com/M-simplifier/clj-ops-ledger>

## Status

Controlled cold public adopter. Counts as stronger public adoption evidence than
the maintainer-built pressure app, but not as independent ecosystem adoption.

## Application Surface

- backend: Ring, Reitit, Integrant, Malli
- frontend: Reagent, re-frame, shadow-cljs
- CI: Clojure tests, ClojureScript release build, and Formsmith check

## Release Coordinate

```clojure
{:git/tag "v0.1.0-pre.3"
 :git/sha "9649e96a0f60c585d9f68608648f63839f5395af"}
```

## Public Evidence

- initial app main CI before Formsmith adoption:
  <https://github.com/M-simplifier/clj-ops-ledger/actions/runs/25537018614>
- PR adopting Formsmith:
  <https://github.com/M-simplifier/clj-ops-ledger/pull/1>
- PR CI:
  <https://github.com/M-simplifier/clj-ops-ledger/actions/runs/25537426519>
- main CI after merge:
  <https://github.com/M-simplifier/clj-ops-ledger/actions/runs/25537477147>

## Before Findings

The first Formsmith check on the app found default safe findings:

```text
files=8 changed=2 findings=12
```

The finding families were:

- `nil/eq`
- `get/keyword-lookup`
- `if/not-condition`

The deeper aggressive preview also found:

- `if/seq-if-let`

## Safety Finding During Adoption

During the first adoption attempt against `v0.1.0-pre.2`, an aggressive seq
rewrite tried to simplify a Hiccup sequence but dropped reader metadata used for
React keys:

```clojure
^{:key (:id entry)}
```

That adoption failure became the safety fix released as `v0.1.0-pre.3`.
Formsmith now blocks source-sensitive autofixes when the form carries metadata,
comments, or reader-discard structure.

## After Findings

After adopting `v0.1.0-pre.3`, the app is clean under both the default CI gate
and the aggressive no-write preview:

```text
clojure -M:formsmith check src test
files=8 changed=0 findings=0

clojure -M:formsmith fix --check --aggressive src test
files=8 changed=0 findings=0
```

The structural adoption verifier also passes with the release coordinate and
the target repo check enabled.

## Suppressions

No rule suppressions or baseline file were needed. The app keeps only
`.formsmith.edn` ignore paths for generated and dependency directories.

# Pressure Projects

## Purpose

Pressure projects are public, runnable applications that use `formsmith` as a
real CI gate. They are not synthetic corpus fixtures. They exist to answer a
harder question:

> does `formsmith` stay useful and low-friction when an AI-driven Clojure app is
> built and maintained under it?

## Current Public Pressure App

- repo: <https://github.com/M-simplifier/formsmith-pressure-app>
- app type: full-stack Clojure/ClojureScript review operations board
- backend surface: Ring, Reitit, Integrant, Malli
- frontend surface: Reagent, re-frame, shadow-cljs
- current Formsmith gate: public release dependency pinned to
  `v0.1.0-pre.6` at
  `89a06b941e32bb9fe78e5fab22d005a5147234b8`
- latest verified GitHub Actions run:
  <https://github.com/M-simplifier/formsmith-pressure-app/actions/runs/25547377831>
- adoption record:
  [docs/adoptions/formsmith-pressure-app.md](./adoptions/formsmith-pressure-app.md)

## Current Controlled Cold Adopter

- repo: <https://github.com/M-simplifier/clj-ops-ledger>
- app type: full-stack Clojure/ClojureScript operations ledger
- backend surface: Ring, Reitit, Integrant, Malli
- frontend surface: Reagent, re-frame, shadow-cljs
- current Formsmith gate: public release dependency pinned to
  `v0.1.0-pre.6` at
  `89a06b941e32bb9fe78e5fab22d005a5147234b8`
- adoption PR:
  <https://github.com/M-simplifier/clj-ops-ledger/pull/1>
- latest release update PR:
  <https://github.com/M-simplifier/clj-ops-ledger/pull/4>
- main CI after merge:
  <https://github.com/M-simplifier/clj-ops-ledger/actions/runs/25547377883>
- adoption record:
  [docs/adoptions/clj-ops-ledger.md](./adoptions/clj-ops-ledger.md)

This app is stronger evidence than a same-purpose pressure app because it was
created as a separate public product surface and then adopted the released
coordinate through a public PR. It is still controlled by the maintainers, so it
does not count as independent external ecosystem adoption.

## Release Consumption

The pressure app currently consumes the `v0.1.0-pre.6` release coordinate that
was current when these adoption records were produced:

```clojure
{:git/tag "v0.1.0-pre.6"
 :git/sha "89a06b941e32bb9fe78e5fab22d005a5147234b8"}
```

Verification:

- pull request CI after switching to the latest release coordinate:
  <https://github.com/M-simplifier/formsmith-pressure-app/actions/runs/25547308379>
- main CI after merge:
  <https://github.com/M-simplifier/formsmith-pressure-app/actions/runs/25547377831>
- pull request CI after switching to the previous release coordinate:
  <https://github.com/M-simplifier/formsmith-pressure-app/actions/runs/25539481348>
- main CI after merge:
  <https://github.com/M-simplifier/formsmith-pressure-app/actions/runs/25539534497>
- earlier pull request CI after switching to the first release coordinate:
  <https://github.com/M-simplifier/formsmith-pressure-app/actions/runs/25536307791>
- earlier main CI after merge:
  <https://github.com/M-simplifier/formsmith-pressure-app/actions/runs/25536357394>
- local pressure-app checks before merge:
  `bb ci` and `clojure -M:formsmith fix --check --aggressive src test`

## Construction Finding

During the initial build, `formsmith fix --check --aggressive src test` found a
CLJS/re-frame idiom in `src/reviewdesk/client.cljs`:

```text
src/reviewdesk/client.cljs:97:4 [if/seq-if-let]
if that tests seq can be written as if-let with not-empty
```

The aggressive fix was applied, then the app passed:

```text
clojure -M:test
npx shadow-cljs release app
clojure -M:formsmith check src test
clojure -M:formsmith fix --check --aggressive src test
```

The latest public CI run verifies the post-fix gate remains clean:
<https://github.com/M-simplifier/formsmith-pressure-app/actions/runs/25547377831>

## What It Proves

- a separate public project can consume the public `formsmith` artifact as a git
  release dependency
- `formsmith check` can run as a CI gate beside tests and a CLJS release build
- `fix --check --aggressive` can be used as an exploratory deeper pass without
  widening the default gate
- real CLJS/re-frame code already returned at least one useful aggressive
  finding during construction, and the generated fix survived tests and release
  build verification
- the controlled cold adopter exposed a metadata-sensitive rewrite bug during
  adoption, and the public `v0.1.0-pre.3` release blocks that class of rewrite
  before applying fixes
- both public adopter apps consumed the `v0.1.0-pre.6` source release with
  green PR and main CI

The current Formsmith source release is `v0.1.0-pre.7`. Updating the controlled
adopters is a separate evidence packet, not implied by this record.

## What It Does Not Prove Yet

- broad ecosystem adoption
- editor integration quality
- package distribution quality
- high rule coverage across many independent production repos
- framework-level enforcement beyond current informational profiles

## Operating Rule

Pressure projects should stay public and minimal. They should contain enough
real app structure to stress the tool, but not private roadmap notes or
unpublished implementation strategy.

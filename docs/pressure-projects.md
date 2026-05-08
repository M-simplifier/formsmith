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
  `v0.1.0-pre.2` at
  `9b1b4fdca1a045ef79495ce7e94106b570f00368`
- latest verified GitHub Actions run:
  <https://github.com/M-simplifier/formsmith-pressure-app/actions/runs/25536357394>

## Release Consumption

The pressure app now consumes the same release coordinate documented for users:

```clojure
{:git/tag "v0.1.0-pre.2"
 :git/sha "9b1b4fdca1a045ef79495ce7e94106b570f00368"}
```

Verification:

- pull request CI after switching to the release coordinate:
  <https://github.com/M-simplifier/formsmith-pressure-app/actions/runs/25536307791>
- main CI after merge:
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
<https://github.com/M-simplifier/formsmith-pressure-app/actions/runs/25536357394>

## What It Proves

- a separate public project can consume the public `formsmith` artifact as a git
  release dependency
- `formsmith check` can run as a CI gate beside tests and a CLJS release build
- `fix --check --aggressive` can be used as an exploratory deeper pass without
  widening the default gate
- real CLJS/re-frame code already returned at least one useful aggressive
  finding during construction, and the generated fix survived tests and release
  build verification

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

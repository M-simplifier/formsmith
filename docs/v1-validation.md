# v1 Validation

## Purpose

This document tracks the evidence for the current `v1` flagship question:

> after formatter and bug lint already passed, does the current `v1` wedge still produce enough safe, explainable improvement to justify a broader public pre-beta share?

## Validation Date

- Current snapshot verified on `2026-05-08`

## Benchmark Roles

### Clean benchmarks

- `dirty-clojure`
- `nextmoon`
- role: proof repos for the narrow `v1` claim
- expectation: ordinary quality gates pass before `formsmith` adds value

### Pressure surface

- `nextmoon`
- role: real-repo pressure and CLJS signal source in addition to being a clean proof repo

## Commands

Run the full validation packet with:

```bash
bb validate-v1
```

This is a maintainer evidence packet. It expects sibling benchmark checkouts for
`dirty-clojure` and `nextmoon`. For a self-contained public clone proof, run
`bb validate-cold-start`.

This packet runs:

- `dirty-clojure`: `npm test`, `npm run lint`, `npm run format:check`
- `nextmoon`: `clojure -M:test`, ad hoc `clj-kondo`, ad hoc `cljfmt check`
- `formsmith`: `bb benchmark-v1`

## Current Result

- current packet status: `bb validate-v1` exits `0` with `failures=0`
- both benchmark repos now pass their baseline gates

### dirty-clojure

- `npm test`: pass
- `npm run lint`: pass
- `npm run format:check`: pass
- `formsmith` pure signal on `src test`: `files=9 changed=0 findings=7`
- `formsmith` safe preview on `src test`: `files=9 changed=1 findings=1`
- `formsmith` aggressive CLJS preview on `frontend/core.cljs`: `files=1 changed=1 findings=5`

### nextmoon

- `clojure -M:test`: pass
- ad hoc `clj-kondo` on `src test deps.edn shadow-cljs.edn`: pass
- ad hoc `cljfmt check` on `src test deps.edn shadow-cljs.edn`: pass
- `formsmith` pure signal on `src`: `files=18 changed=0 findings=12`
- `formsmith` safe preview on `src`: `files=18 changed=1 findings=1`
- `formsmith` aggressive CLJS preview on `src/nextmoon/ui/core.cljs`: `files=1 changed=1 findings=1`

## Read

- `dirty-clojure` and `nextmoon` now both validate the narrow wedge honestly: ordinary tooling passes, and `formsmith` still finds meaningful local improvements.
- cleaning `nextmoon` reduced the remaining `formsmith` signal from `17` to `12` and the safe preview from `6` to `1`, which is good evidence that the current findings are tighter and less entangled with ordinary-tool noise.
- the strongest current public statement is now "two clean benchmark repos pass ordinary tooling and still yield meaningful `formsmith` signal."
- `nextmoon` still adds value beyond the raw count because it remains the stronger real-repo pressure and CLJS surface.

## Next Decision

- decide whether the current two-repo proof is enough for a broader public pre-beta share
- if not, run one owner-run cold AI operator trial before widening the audience

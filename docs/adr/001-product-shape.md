# ADR 001: Product Shape

## Status

Accepted

## Decision

`formsmith` is a linter-first rewrite tool with an explicit formatter phase, not a formatter with a few lint rules attached.

## Why

- formatter-only differentiation is weak in the current ecosystem
- the strongest product gap is explainable, safe style rewrites
- semantic transformation and formatting should still feel like one command to the user

## Consequences

- the rewrite engine is the architectural center
- the formatter sits behind a replaceable backend
- rules must carry safety and explanation data from the beginning


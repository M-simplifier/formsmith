# Vision

`formsmith` exists for a gap in the Clojure tooling landscape.

Existing tools are strong, but they split the problem:

- formatters normalize layout
- linters report issues
- analyzers understand code

What Clojure still lacks is a tool that turns established style judgment into executable, safe, explainable rewrites.

## Product Claim

`formsmith` makes Clojure code better in one pass:

1. understand the form
2. rewrite it when a stronger expression is obvious
3. format the resulting code canonically

The user experience should feel config-free and immediately useful, while still allowing teams to tune edges later.

## Non-Goals For The First Phase

- replacing every existing formatter immediately
- inventing a highly dynamic style language before we have the right rule model
- aggressive rewrites whose safety story is weak

## Standard For Success

- users can run one command and see code improve, not just reflow
- autofixes are trusted because they are conservative and legible
- the resulting code looks intentional, not machine-averaged


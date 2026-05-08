# formsmith-pressure-app Adoption Record

## Repository

<https://github.com/M-simplifier/formsmith-pressure-app>

## Status

Maintainer-built pressure project. Counts as L3 pressure evidence, not
independent L4 adoption evidence.

## Release Coordinate

```clojure
{:git/tag "v0.1.0-pre.6"
 :git/sha "89a06b941e32bb9fe78e5fab22d005a5147234b8"}
```

## Public Evidence

- PR switching to `v0.1.0-pre.4`:
  <https://github.com/M-simplifier/formsmith-pressure-app/pull/3>
- PR CI:
  <https://github.com/M-simplifier/formsmith-pressure-app/actions/runs/25539481348>
- main CI after merge:
  <https://github.com/M-simplifier/formsmith-pressure-app/actions/runs/25539534497>
- PR switching to `v0.1.0-pre.5`:
  <https://github.com/M-simplifier/formsmith-pressure-app/pull/4>
- PR CI:
  <https://github.com/M-simplifier/formsmith-pressure-app/actions/runs/25543016191>
- main CI after merge:
  <https://github.com/M-simplifier/formsmith-pressure-app/actions/runs/25543087196>
- PR switching to `v0.1.0-pre.6`:
  <https://github.com/M-simplifier/formsmith-pressure-app/pull/5>
- PR CI:
  <https://github.com/M-simplifier/formsmith-pressure-app/actions/runs/25547308379>
- main CI after merge:
  <https://github.com/M-simplifier/formsmith-pressure-app/actions/runs/25547377831>
- PR switching to `v0.1.0-pre.2`:
  <https://github.com/M-simplifier/formsmith-pressure-app/pull/2>
- PR CI:
  <https://github.com/M-simplifier/formsmith-pressure-app/actions/runs/25536307791>
- main CI after merge:
  <https://github.com/M-simplifier/formsmith-pressure-app/actions/runs/25536357394>

## Finding Summary

During initial construction, aggressive preview found:

```text
src/reviewdesk/client.cljs:97:4 [if/seq-if-let]
if that tests seq can be written as if-let with not-empty
```

The fix was applied, and the latest release-coordinate CI runs remain clean:

```text
files=9 changed=0 findings=0
```

## Suppressions

No rule suppressions or baseline file were needed. The app keeps only
`.formsmith.edn` ignore paths for generated and dependency directories.

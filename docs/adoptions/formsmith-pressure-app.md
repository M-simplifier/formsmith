# formsmith-pressure-app Adoption Record

## Repository

<https://github.com/M-simplifier/formsmith-pressure-app>

## Status

Maintainer-built pressure project. Counts as L3 pressure evidence, not
independent L4 adoption evidence.

## Release Coordinate

```clojure
{:git/tag "v0.1.0-pre.2"
 :git/sha "9b1b4fdca1a045ef79495ce7e94106b570f00368"}
```

## Public Evidence

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

The fix was applied, and later release-coordinate CI runs remained clean:

```text
files=9 changed=0 findings=0
```

## Suppressions

No rule suppressions or baseline file were needed. The app keeps only
`.formsmith.edn` ignore paths for generated and dependency directories.

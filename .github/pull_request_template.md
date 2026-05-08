## Scope

Describe the maintainer-owned packet and the public claim, rule behavior, or
documentation surface it changes.

## Checks

- [ ] `bb ci`
- [ ] `bb validate-cold-start` if first-run docs or demo code changed
- [ ] `bb benchmark-v1` / `bb validate-v1` if benchmark claims or rule behavior changed
- [ ] public export audited when publication surface changed

## Public Boundary

Confirm that this PR does not publish private roadmap, local paths, unpublished
claims, or internal Codex operation notes.

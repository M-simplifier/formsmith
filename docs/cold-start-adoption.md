# Cold-Start Adoption

## Purpose

This document tracks a narrower question than the main benchmark packet:

> can a fresh copy of the `formsmith` repo reach meaningful repo-only output from the public first-run path, without sibling benchmark repos?

That is useful adoption evidence, but it is not the same thing as broader human adoption proof.

## Evidence Tiers

### Tier 1: Mechanical first-run proof

This is the maintainer-side gate we can run today.

It asks:

- does a fresh disposable copy of the current repo work?
- do the documented repo-only demo commands succeed?
- does that path produce meaningful CLJ and CLJS signal?

Run it with:

```bash
bb validate-cold-start
```

### Tier 2: Owner-run cold AI operator trial

This is the current intended pre-beta readiness gate.

It should ask a fresh AI session to use only:

- `README.md`
- `docs/quickstart.md`
- `docs/cold-start-demo.md`
- `AGENTS.md`

The session should not receive chat hints, outside repo knowledge, or unpublished notes.

### Tier 3: External human adoption

This is also still open.

The stronger claim would be:

- at least one external human can clone the repo, follow the public docs, and reach a clear adoption judgment without maintainer help

## Current Mechanical Packet

`bb validate-cold-start` runs `scripts/validate-cold-start.sh`.

That packet:

1. creates a fresh disposable workspace copy of the current repo
2. runs only the repo-only public demo flow
3. checks that meaningful output still appears
4. fails if the first-run path or expected output drifts unexpectedly

The packet intentionally does not touch:

- sibling benchmark repos
- maintainer-only sibling-repo trial helpers
- the stronger `v1` wedge validation in `bb validate-v1`

## Current Verified Result

- current packet status: `bb validate-cold-start` exits `0`
- current snapshot verified on `2026-05-08`
- repo-only first-run path:
  - `lint` => `files=3 changed=0 findings=9`
  - `check` => `files=3 changed=2 findings=7`
  - `preview` => `files=3 changed=2 findings=7`
  - `aggressive-preview` => `files=1 changed=1 findings=4`

## Honest Read

What this proves:

- the repo-only first-run path is reproducible on the current machine
- the public demo does not rely on sibling benchmark repos
- the first-run surface reaches both CLJ and CLJS findings quickly

What this does not prove:

- that an outside human will find the docs immediately obvious
- that package installation or editor integration are solved
- that the current onboarding copy is already good enough for a broad share by itself

What Tier 2 should prove before broader public pre-beta share:

- a fresh AI session can discover the intended workflow from the public repo alone
- it can run the core loop without human steering
- it preserves the safety boundary correctly:
  - default `fix` is conservative
  - `--aggressive` is explicit
  - benchmark validation and repo-only demo validation are not confused

## Current Next Gap

The remaining adoption evidence gap is no longer "can the repo run its own demo?".

It is now:

- owner-run cold AI operator trial quality
- external human reaction quality

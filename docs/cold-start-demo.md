# Cold-Start Demo

This is the public first-run path for a fresh clone of `formsmith`.

It uses only files that live inside this repository.

## Purpose

This demo is for one question:

- can a new user reach meaningful `formsmith` output from the repo alone?

It is not benchmark proof.

For benchmark proof, use:

- `bb benchmark-v1`
- `bb validate-v1`

For the maintainer-side validation packet that re-runs this path from a fresh disposable repo copy, use:

- `bb validate-cold-start`

## Requirements

- JDK
- Clojure CLI
- `bash`

If you are driving this through an AI coding agent, point it at `AGENTS.md` before the first run. That file is the repo's public AI operator guide.

## First Run

From the `formsmith` repo:

```bash
bash scripts/cold-start-demo.sh prepare
bash scripts/cold-start-demo.sh lint
bash scripts/cold-start-demo.sh check
bash scripts/cold-start-demo.sh preview
```

That creates a disposable workspace at:

```text
playgrounds/cold-start-demo
```

## What You Should See

The bundled demo currently shows:

- multiple safe findings across CLJ and CLJS in `lint`
- a smaller safe no-write patch in `preview`
- extra CLJS-side findings in `aggressive-preview`

Try the stronger preview with:

```bash
bash scripts/cold-start-demo.sh aggressive-preview
```

Apply the safe rewrites with:

```bash
bash scripts/cold-start-demo.sh fix
```

## Reset

To throw away your edits and start over:

```bash
bash scripts/cold-start-demo.sh prepare
```

## Why This Exists

The repo-only demo is intentionally separate from:

- benchmark validation
- sibling benchmark repos
- maintainer-only sibling-repo trial helpers

It exists so the public "first five minutes" path does not depend on any local setup outside this repo.

See [cold-start adoption](./cold-start-adoption.md) for what this demo currently proves and what it still does not prove.

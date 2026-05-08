# Review Study Records

This directory is for completed public review-elimination study records.

Use `template.md` when adding a new record. A record should be committed only
when the public evidence is reproducible from URLs and the study does not rely
on private chat, local screenshots, or maintainer memory.

To start a new record without dropping required fields:

```bash
REVIEW_STUDY_REPO=https://github.com/org/repo bb new-review-study
```

The scaffold verifier runs with placeholder allowance. Before committing a
completed record, replace every placeholder, record `contracts=0` when no
contracts are present, and run the completed-record verifier below.

Verify a completed record with:

```bash
REVIEW_STUDY=docs/review-studies/<record>.md bb verify-review-study
```

To also verify linked GitHub Actions runs, use:

```bash
FORMSMITH_REVIEW_STUDY_VERIFY_GITHUB=1 \
REVIEW_STUDY=docs/review-studies/<record>.md \
bb verify-review-study
```

The verifier checks structure, required classification labels, public GitHub
evidence links, finding-summary fields, classification counts, placeholder
removal, and optional GitHub Actions run conclusions. It does not decide whether
the study is strong enough for L5 by itself.

Current records:

- [`clj-ops-ledger-2026-05-08.md`](./clj-ops-ledger-2026-05-08.md)
  Controlled public adopter; adoption evidence exists, review-elimination
  evidence does not.
- [`formsmith-pressure-app-2026-05-08.md`](./formsmith-pressure-app-2026-05-08.md)
  Maintainer pressure project; useful CI pressure, not independent adoption.

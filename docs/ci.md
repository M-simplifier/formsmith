# CI

## GitHub Actions

Use this as the minimal GitHub Actions gate for a project that already has a
working Clojure CLI setup:

```yaml
name: Formsmith

on:
  pull_request:
    branches: [main]
  push:
    branches: [main]

permissions:
  contents: read

jobs:
  formsmith:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6

      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: "21"

      - name: Install Clojure CLI
        run: |
          curl -L -O https://github.com/clojure/brew-install/releases/latest/download/linux-install.sh
          chmod +x linux-install.sh
          sudo ./linux-install.sh

      - name: Cache Clojure dependencies
        uses: actions/cache@v5
        with:
          path: |
            ~/.m2/repository
            ~/.gitlibs
            ~/.deps.clj
            .cpcache
          key: clojure-${{ runner.os }}-${{ hashFiles('deps.edn', '.formsmith.edn') }}
          restore-keys: |
            clojure-${{ runner.os }}-

      - name: Formsmith check
        run: clojure -M:formsmith check src test
```

The target repo should define the `:formsmith` alias from
[`docs/install.md`](./install.md).

See [`examples/github-actions/formsmith-check.yml`](../examples/github-actions/formsmith-check.yml)
for a copyable workflow file.

## Recommended Gates

For production projects:

```bash
clojure -M:formsmith check src test
clojure -M:formsmith contracts --json src test > target/formsmith-contracts.json
```

Use `check` as the required status. Treat `contracts` as an artifact for humans
or AI agents; it exits `0` because contracts are not mechanical fixes.

For deeper optional review:

```bash
clojure -M:formsmith fix --check --aggressive src test
```

Keep this optional until the relevant semantic-pattern rules have enough
project-specific evidence to be promoted into the default gate.

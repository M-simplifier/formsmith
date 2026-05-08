# Install

## Status

`formsmith` is still pre-beta. The current supported distribution path is a
versioned GitHub source release consumed through Clojure CLI git dependencies.

Clojars, editor extensions, and standalone binaries are not published yet.

## Clojure CLI Alias

Add a `:formsmith` alias to the target repository:

```clojure
{:aliases
 {:formsmith
  {:extra-deps {io.github.M-simplifier/formsmith
                {:git/url "https://github.com/M-simplifier/formsmith.git"
                 :git/tag "v0.1.0-pre.4"
                 :git/sha "a2449935781fc6f3b2b4451aa3e8e30e0bbc9abf"}}
   :main-opts ["-m" "formsmith.main"]}}}
```

Then run:

```bash
clojure -M:formsmith check src test
clojure -M:formsmith fix --check --aggressive src test
clojure -M:formsmith baseline src test -o .formsmith-baseline.edn
```

Clojure CLI git dependencies require a `:git/sha`. Pin both the release tag and
the commit SHA that the release points to. The tag makes the intended version
readable; the SHA keeps the dependency immutable.

## Existing Repos

For an existing codebase, start with a baseline:

```bash
clojure -M:formsmith baseline src test -o .formsmith-baseline.edn
```

Then commit:

```clojure
{:baseline ".formsmith-baseline.edn"
 :ignore-paths ["target" "resources/public/js" "node_modules"]}
```

After that, `check` blocks only new unsuppressed findings.

## New Repos

For a new repo, prefer no baseline:

```clojure
{:ignore-paths ["target" "resources/public/js" "node_modules"]}
```

Then make `formsmith check` part of the normal CI gate.

## What This Does Not Install

The source release is not a Clojars package, editor extension, native binary, or
formatter plugin. It is the supported pre-beta way to run the current CLI from a
normal Clojure project while the rule surface is still being proven.

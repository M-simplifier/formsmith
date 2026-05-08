const demos = {
  safe: {
    beforeFile: "src/ui.cljs",
    afterFile: "preview",
    command: "clojure -M -m formsmith.main fix --check .",
    before: `(defn banner [ready? label]
  (if (not ready?)
    [:p.muted "Waiting"]
    (str label)))`,
    after: `(defn banner [ready? label]
  (if-not ready?
    [:p.muted "Waiting"]
    label))`
  },
  guarded: {
    beforeFile: "src/search.cljs",
    afterFile: "guarded preview",
    command: "clojure -M -m formsmith.main fix --check --guarded src",
    before: `(defn results [items]
  (when (seq items)
    [:ul
     (for [item items]
       [:li item])]))`,
    after: `(defn results [items]
  (when-let [items (not-empty items)]
    [:ul
     (for [item items]
       [:li item])]))`
  },
  contract: {
    beforeFile: "src/forms.cljs",
    afterFile: "LLM refactor contract",
    command: "clojure -M -m formsmith.main contracts src",
    before: `(empty? (str/trim value))`,
    after: `contract-id=llm-refactor:empty/trim-blank:src/forms.cljs:1:1
blocked-by=nil semantics can differ
llm-task=Inspect the surrounding code and decide whether blank? preserves the intended behavior.`
  },
  cljd: {
    beforeFile: "src/mobile.cljd",
    afterFile: "Flutter UI rewrite",
    command: "clojure -M -m formsmith.main fix --check --rewrite-only src/mobile.cljd",
    before: `(m/IgnorePointer
  .ignoring disabled?
  .child
  (m/AnimatedContainer
    .duration duration
    .child
    (m/FloatingActionButton
      .onPressed submit
      .child
      (m/Icon m/Icons.create))))`,
    after: `(f/nest
  (m/IgnorePointer .ignoring disabled?)
  (m/AnimatedContainer .duration duration)
  (m/FloatingActionButton .onPressed submit)
  (m/Icon m/Icons.create))`
  },
  profiles: {
    beforeFile: "src/app/ui.cljs",
    afterFile: "framework profile output",
    command: "clojure -M -m formsmith.main profiles src",
    before: `(ns app.mobile
  (:require ["package:flutter/material.dart" :as m]
            [cljd.flutter :as f]
            [io.factorhouse.hsx.core :as hsx]
            [io.factorhouse.rfx.core :as rfx]))`,
    after: `ClojureDart (clojuredart) category=mobile-ui evidence=2
HSX (hsx) category=cljs-view evidence=1
RFX (rfx) category=cljs-state evidence=1
frameworks=3`
  }
};

const beforeCode = document.querySelector("#before-code");
const afterCode = document.querySelector("#after-code");
const beforeFile = document.querySelector("#before-file");
const afterFile = document.querySelector("#after-file");
const command = document.querySelector("#demo-command");
const tabs = Array.from(document.querySelectorAll(".demo-tab"));

function setDemo(name) {
  const demo = demos[name] || demos.safe;
  beforeCode.textContent = demo.before;
  afterCode.textContent = demo.after;
  beforeFile.textContent = demo.beforeFile;
  afterFile.textContent = demo.afterFile;
  command.textContent = demo.command;

  for (const tab of tabs) {
    const active = tab.dataset.demo === name;
    tab.classList.toggle("active", active);
    tab.setAttribute("aria-pressed", active ? "true" : "false");
  }
}

for (const tab of tabs) {
  tab.addEventListener("click", () => setDemo(tab.dataset.demo));
}

for (const button of document.querySelectorAll("[data-copy-target]")) {
  button.addEventListener("click", async () => {
    const target = document.querySelector(`#${button.dataset.copyTarget}`);
    if (!target || !navigator.clipboard) {
      return;
    }
    await navigator.clipboard.writeText(target.textContent);
    button.textContent = "Copied";
    window.setTimeout(() => {
      button.textContent = "Copy";
    }, 1100);
  });
}

setDemo("safe");

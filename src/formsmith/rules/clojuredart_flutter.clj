(ns formsmith.rules.clojuredart-flutter
  (:require [clojure.string :as str]
            [formsmith.finding :as finding]
            [formsmith.rules.helpers :as helpers]
            [formsmith.source :as source]
            [rewrite-clj.zip :as z]))

(defn- cljd-file? [context]
  (str/ends-with? (str (:file context)) ".cljd"))

(defn- flutter-alias [context]
  (source/alias-for-context context 'cljd.flutter))

(defn- flutter-head? [context head target-name]
  (let [alias (flutter-alias context)]
    (or (= (symbol "cljd.flutter" target-name) head)
        (and alias (= (symbol (str alias) target-name) head)))))

(defn- deprecated-alpha-required? [context]
  (or (source/required-in-context? context 'cljd.flutter.alpha)
      (source/required-in-context? context 'cljd.flutter.alpha2)))

(defn- ns-form? [zloc]
  (and (helpers/list-form? zloc)
       (= 'ns (helpers/head-symbol zloc))))

(defn- child-key? [form]
  (or (= :child form)
      (= '.child form)))

(defn- widget-constructor-head? [head]
  (let [name-part (some-> head name)]
    (and (symbol? head)
         (namespace head)
         (not= "nest" name-part)
         (boolean (re-find #"^[A-Z]" (str/replace name-part #"\.$" ""))))))

(defn- constructor-name [head]
  (some-> head name (str/replace #"\.$" "")))

(defn- widget-form? [zloc]
  (and (helpers/list-form? zloc)
       (widget-constructor-head? (helpers/head-symbol zloc))))

(defn- trailing-child-pair [zloc]
  (let [children (helpers/child-locs zloc)
        n (count children)]
    (when (>= n 3)
      (let [key-loc (nth children (- n 2))
            child-loc (nth children (dec n))]
        (when (child-key? (z/sexpr key-loc))
          {:children children
           :key-loc key-loc
           :child-loc child-loc})))))

(defn- without-trailing-child-source [{:keys [children]}]
  (let [kept (subvec (vec children) 0 (- (count children) 2))]
    (str "(" (str/join " " (map helpers/node-string kept)) ")")))

(defn- flatten-child-chain [zloc]
  (when (widget-form? zloc)
    (loop [loc zloc
           widgets []]
      (if-let [{:keys [child-loc] :as match} (trailing-child-pair loc)]
        (recur child-loc (conj widgets (without-trailing-child-source match)))
        (let [widgets (conj widgets (helpers/node-string loc))]
          (when (>= (count widgets) 3)
            widgets))))))

(defn- nest-source [alias widgets]
  (str "(" alias "/nest\n  " (str/join "\n  " widgets) ")"))

(defn- cljd-nest-match [zloc context]
  (when (cljd-file? context)
    (when-let [alias (flutter-alias context)]
      (when-let [widgets (flatten-child-chain zloc)]
        {:rule-id :clojuredart/widget-child-chain
         :message "Nested Flutter :child/.child widget chains can be flattened with cljd.flutter/nest"
         :replacement-source (nest-source alias widgets)
         :safety :syntax-safe}))))

(defn- deprecated-alpha-match [zloc context]
  (when (and (cljd-file? context)
             (ns-form? zloc)
             (deprecated-alpha-required? context))
    {:rule-id :clojuredart/deprecated-flutter-alpha
     :message "Deprecated cljd.flutter alpha namespaces should be migrated to cljd.flutter"
     :safety :unsafe
     :contract {:blocked-by ["The tool cannot prove which alpha APIs are still used or whether a direct namespace swap is sufficient."]
                :llm-task "Inspect cljd.flutter.alpha or cljd.flutter.alpha2 usages and migrate them to cljd.flutter with the smallest behavior-preserving change."
                :acceptance ["The namespace requires cljd.flutter instead of deprecated alpha namespaces."
                             "ClojureDart build or relevant widget tests pass."
                             "Formsmith check is clean on the touched .cljd files."]}}))

(defn- f-widget-form? [zloc context]
  (and (helpers/list-form? zloc)
       (flutter-head? context (helpers/head-symbol zloc) "widget")))

(defn- inside-f-widget-option-value? [zloc context option]
  (loop [loc zloc]
    (when-let [parent (z/up loc)]
      (if (f-widget-form? parent context)
        (let [children (helpers/child-locs parent)
              idx (helpers/child-index parent loc)
              option-loc (when (and idx (pos? idx))
                           (nth children (dec idx) nil))]
          (= option (some-> option-loc z/sexpr)))
        (recur parent)))))

(defn- widget-options-and-body [zloc]
  (let [children (subvec (vec (helpers/child-locs zloc)) 1)
        option? #(and % (keyword? (z/sexpr %)))]
    (loop [remaining children
           options []]
      (let [[option-loc value-loc & more] remaining]
        (if (and (option? option-loc) value-loc)
          (recur (vec more) (conj options (z/sexpr option-loc)))
          {:options (set options)
           :body remaining})))))

(defn- f-widget-size-match [zloc context]
  (when (and (cljd-file? context)
             (f-widget-form? zloc context))
    (let [{:keys [body]} (widget-options-and-body zloc)]
      (when (>= (count body) 9)
        {:rule-id :clojuredart/widget-body-extraction
         :message "Large f/widget bodies should be reviewed for component extraction"
         :safety :unsafe
         :suggested-source "(defn smaller-widget [...] (f/widget ...))"
         :contract {:blocked-by ["Component extraction changes names, parameter boundaries, and widget identity decisions."]
                    :llm-task "Split this large f/widget body into smaller named widgets while preserving state ownership and Flutter keys."
                    :acceptance ["The extracted widgets have stable, domain-specific names."
                                 "Stateful options remain at the owner component."
                                 "ClojureDart build or widget tests pass."]}}))))

(defn- f-widget-local-atom-match [zloc context]
  (when (and (cljd-file? context)
             (f-widget-form? zloc context)
             (str/includes? (helpers/node-string zloc) "(atom "))
    {:rule-id :clojuredart/widget-local-atom-state
     :message "Local atom state inside f/widget should usually use the :state option"
     :safety :unsafe
     :suggested-source "(f/widget :state [state initial-value] ...)"
     :contract {:blocked-by ["The state binding and all deref/swap/reset sites must move together."]
                :llm-task "Migrate local widget-owned atom state to the f/widget :state option when the atom exists only for this widget."
                :acceptance ["Widget-owned state is declared in :state."
                             "Existing deref and update behavior is preserved."
                             "ClojureDart build or widget tests pass."]}}))

(defn- builder-constructor-match [zloc context]
  (when (and (cljd-file? context)
             (helpers/list-form? zloc))
    (let [head (helpers/head-symbol zloc)
          ctor (constructor-name head)]
      (when (#{"Builder" "StatefulBuilder"} ctor)
        {:rule-id :clojuredart/builder-to-widget
         :message "Flutter Builder and StatefulBuilder forms are candidates for f/widget"
         :safety :unsafe
         :suggested-source "(f/widget :context context ...)"
         :contract {:blocked-by ["The callback argument shape and captured state determine the right f/widget options."]
                    :llm-task "Replace Builder/StatefulBuilder boilerplate with f/widget when the widget body can be expressed directly."
                    :acceptance ["BuildContext handling is explicit through :context or an equivalent f/widget option."
                                 "No callback arity or state update behavior changes."
                                 "ClojureDart build or widget tests pass."]}}))))

(defn- controller-constructor-match [zloc context]
  (when (and (cljd-file? context)
             (helpers/list-form? zloc)
             (not (inside-f-widget-option-value? zloc context :with)))
    (let [ctor (constructor-name (helpers/head-symbol zloc))]
      (when (#{"TextEditingController" "ScrollController" "PageController" "TabController"} ctor)
        {:rule-id :clojuredart/controller-without-widget-with
         :message "Flutter controllers should be reviewed for f/widget :with lifecycle ownership"
         :safety :unsafe
         :suggested-source "(f/widget :with [controller (m/TextEditingController.)] ...)"
         :contract {:blocked-by ["The tool cannot prove whether this controller is widget-owned, externally owned, or intentionally long-lived."]
                    :llm-task "If this controller is owned by the widget, move it into f/widget :with so initialization and disposal are explicit."
                    :acceptance ["Widget-owned controllers are initialized with :with."
                                 "Disposal semantics are explicit and unchanged."
                                 "ClojureDart build or widget tests pass."]}}))))

(defn- animation-controller-match [zloc context]
  (when (and (cljd-file? context)
             (helpers/list-form? zloc)
             (not (inside-f-widget-option-value? zloc context :with)))
    (let [ctor (constructor-name (helpers/head-symbol zloc))]
      (when (= "AnimationController" ctor)
        {:rule-id :clojuredart/animation-controller-ticker
         :message "AnimationController ownership should be reviewed for f/widget :ticker or :tickers"
         :safety :unsafe
         :suggested-source "(f/widget :ticker ticker :with [controller (m/AnimationController. .vsync ticker ...)] ...)"
         :contract {:blocked-by ["Ticker ownership and controller disposal must be decided with the surrounding widget lifecycle."]
                    :llm-task "Move widget-owned animation controllers to f/widget :ticker/:tickers plus :with where appropriate."
                    :acceptance ["TickerProvider ownership is explicit."
                                 "AnimationController disposal is explicit."
                                 "ClojureDart build or animation/widget tests pass."]}}))))

(defn- widget-of-context-match [zloc context]
  (when (and (cljd-file? context)
             (helpers/list-form? zloc)
             (not (inside-f-widget-option-value? zloc context :inherit))
             (not (inside-f-widget-option-value? zloc context :get)))
    (let [head (helpers/head-symbol zloc)
          head-name (some-> head name)]
      (when (and head-name
                 (str/ends-with? head-name ".of"))
        {:rule-id :clojuredart/widget-of-context-helper
         :message "Widget.of(context) lookups should be reviewed for f/widget :inherit or :get"
         :safety :unsafe
         :suggested-source "(f/widget :context ctx :inherit [theme (m/Theme.of ctx)] ...)"
         :contract {:blocked-by ["The correct helper depends on whether the inherited value should trigger rebuilds and where BuildContext is owned."]
                    :llm-task "Use f/widget :context, :inherit, or :get to make BuildContext and inherited Flutter lookups explicit."
                    :acceptance ["BuildContext ownership is visible in the f/widget options."
                                 "Inherited dependencies still rebuild as intended."
                                 "ClojureDart build or widget tests pass."]}}))))

(defn- match [zloc context]
  (or (cljd-nest-match zloc context)
      (deprecated-alpha-match zloc context)
      (f-widget-local-atom-match zloc context)
      (f-widget-size-match zloc context)
      (builder-constructor-match zloc context)
      (controller-constructor-match zloc context)
      (animation-controller-match zloc context)
      (widget-of-context-match zloc context)))

(def rule
  {:id :clojuredart/flutter-ui
   :summary "Prefer canonical cljd.flutter UI forms such as f/nest for long widget child chains"
   :safety :syntax-safe
   :kinds #{:rewrite}
   :check (fn [zloc]
            (helpers/list-form? zloc))
   :apply (fn [zloc context]
            (if-let [{:keys [rule-id message replacement-source safety suggested-source contract]} (match zloc context)]
              (let [before (helpers/node-string zloc)
                    applied? (and replacement-source
                                  (helpers/autofix-allowed? context zloc safety))
                    updated (if applied?
                              (helpers/replace-with-string zloc replacement-source)
                              zloc)
                    after (helpers/node-string updated)]
                {:zloc updated
                 :finding (finding/make
                           {:rule-id rule-id
                            :message message
                            :safety safety
                            :severity :warning
                            :source :formsmith
                            :file (:file context)
                            :line (helpers/line zloc)
                            :column (helpers/column zloc)
                            :applied? applied?
                            :kind :rewrite
                            :suggested-source (or replacement-source suggested-source)
                            :contract contract
                            :before before
                            :after after})})
              {:zloc zloc
               :finding nil}))})

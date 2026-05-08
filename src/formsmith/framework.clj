(ns formsmith.framework
  (:require [clojure.string :as str]
            [formsmith.fs :as fs]))

(def profiles
  [{:id "re-frame"
    :label "re-frame"
    :category "cljs-state"
    :exact #{'re-frame.core}
    :canonical-guidance ["Keep event, effect, coeffect, and subscription registrations in predictable namespaces."
                         "Prefer explicit effect boundaries over embedding side effects in view components."
                         "Keep subscription functions pure and small enough to review mechanically."]}
   {:id "reagent"
    :label "Reagent"
    :category "cljs-view"
    :exact #{'reagent.core}
    :canonical-guidance ["Keep component view functions separate from app-state registration code."
                         "Prefer local Hiccup helpers over deeply nested anonymous view forms when structure repeats."
                         "Keep ratom state ownership explicit at component boundaries."]}
   {:id "hsx"
    :label "HSX"
    :category "cljs-view"
    :exact #{'io.factorhouse.hsx.core}
    :prefixes ["io.factorhouse.hsx."]
    :canonical-guidance ["Keep HSX components as ordinary React function components with stable identities."
                         "Prefer React hooks such as useState, useEffect, and useRef over Reagent ratom patterns."
                         "Use hsx/create-element and hsx/reactify-component at interop boundaries, not as ad hoc wrappers inside render bodies."]}
   {:id "rfx"
    :label "RFX"
    :category "cljs-state"
    :exact #{'io.factorhouse.rfx.core
             'io.factorhouse.re-frame-bridge.core}
    :prefixes ["io.factorhouse.rfx."]
    :canonical-guidance ["Prefer explicit RFX contexts for application edges and tests."
                         "Use RFX hooks from React components instead of treating subscriptions as derefable Reagent reactions."
                         "Use snapshot and snapshot-sub only at non-React integration boundaries where non-reactive reads are intended."]}
   {:id "clojuredart"
    :label "ClojureDart"
    :category "mobile-ui"
    :exact #{'cljd.flutter
             'cljd.flutter.alpha
             'cljd.flutter.alpha2
             "package:flutter/material.dart"}
    :prefixes ["cljd." "package:flutter/"]
    :canonical-guidance ["Prefer cljd.flutter over deprecated cljd.flutter.alpha namespaces."
                         "Flatten long single-child Flutter widget chains with f/nest when the cljd.flutter alias is explicit."
                         "Keep widget macro lifecycle options such as :state, :watch, and :with visible at component boundaries."]}
   {:id "ring"
    :label "Ring"
    :category "http"
    :prefixes ["ring."]
    :canonical-guidance ["Keep handler construction, middleware stacking, and response helpers in distinct namespaces."
                         "Prefer data-oriented response construction over ad hoc map assembly when Ring helpers fit."
                         "Make middleware order visible and reviewable."]}
   {:id "reitit"
    :label "Reitit"
    :category "routing"
    :prefixes ["reitit."]
    :canonical-guidance ["Keep route data as data and route handlers as named functions."
                         "Separate route table construction from handler implementation when tables grow."
                         "Keep coercion, middleware, and handler concerns visible in route data."]}
   {:id "integrant"
    :label "Integrant"
    :category "system"
    :exact #{'integrant.core}
    :canonical-guidance ["Keep init-key and halt-key! responsibilities narrow."
                         "Prefer explicit system keys over hidden global lifecycle state."
                         "Keep configuration data separate from runtime component construction."]}
   {:id "malli"
    :label "Malli"
    :category "schema"
    :prefixes ["malli."]
    :canonical-guidance ["Keep shared schemas named and close to their domain boundary."
                         "Prefer schema-driven validation at IO boundaries."
                         "Avoid burying large anonymous schemas inside handler bodies."]}])

(defn- symbol-label [value]
  (cond
    (symbol? value) (str value)
    (keyword? value) (if-let [ns-part (namespace value)]
                       (str ns-part "/" (name value))
                       (name value))
    (some? value) (str value)
    :else nil))

(defn- namespace-matches? [{:keys [exact prefixes]} ns-sym]
  (let [ns-name (str ns-sym)]
    (or (contains? exact ns-sym)
        (some #(str/starts-with? ns-name %) prefixes))))

(defn- evidence-for [profile namespace-deps]
  (->> namespace-deps
       (filter #(namespace-matches? profile (:to %)))
       (mapv (fn [{:keys [from to alias file line column]}]
               {:from (symbol-label from)
                :to (symbol-label to)
                :alias (symbol-label alias)
                :file (fs/display-path file)
                :line line
                :column column}))))

(defn detect [namespace-deps]
  (->> profiles
       (keep (fn [{:keys [id label category canonical-guidance] :as profile}]
               (when-let [evidence (not-empty (evidence-for profile namespace-deps))]
                 {:id id
                  :label label
                  :category category
                  :evidence evidence
                  :canonical-guidance canonical-guidance})))
       (sort-by :id)
       vec))

(defn summarize [frameworks]
  {:frameworks (count frameworks)})

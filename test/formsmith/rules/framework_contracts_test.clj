(ns formsmith.rules.framework-contracts-test
  (:require [clojure.test :refer [deftest is testing]]
            [formsmith.engine :as engine]))

(defn- rule-ids [source file]
  (-> (engine/process-source source {:file file :mode :lint})
      :findings
      (->> (mapv :rule-id))))

(deftest emits-namespace-and-hiccup-contracts
  (testing "mixed framework namespaces are runnable responsibility-shape support"
    (let [ids (rule-ids "(ns demo.mixed
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [reitit.ring :as ring]
            [malli.core :as m]))"
                        "src/demo/mixed.clj")]
      (is (contains? (set ids) :namespace/mixed-framework-responsibility))))
  (testing "Hiccup attribute shape emits a contract"
    (let [ids (rule-ids "(ns demo.ui
  (:require [reagent.core :as r]))

(defn view []
  [:div {:class \"panel panel-active\"} \"ok\"])"
                        "src/demo/ui.cljs")]
      (is (contains? (set ids) :hiccup/attribute-shape)))))

(deftest emits-reagent-and-re-frame-contracts
  (testing "generic Reagent ratom state is reviewed outside HSX"
    (let [ids (rule-ids "(ns demo.reagent
  (:require [reagent.core :as r]))

(defn counter []
  (let [n (r/atom 0)]
    [:button @n]))"
                        "src/demo/reagent.cljs")]
      (is (contains? (set ids) :reagent/local-ratom-state))))
  (testing "Reagent form-2 components are supported as contracts"
    (let [ids (rule-ids "(ns demo.reagent
  (:require [reagent.core :as r]))

(defn panel [title]
  (fn []
    [:section title]))"
                        "src/demo/reagent.cljs")]
      (is (contains? (set ids) :reagent/form-2-component))))
  (testing "reg-event-db side effects are reported"
    (let [ids (rule-ids "(ns demo.events
  (:require [re-frame.core :as rf]))

(rf/reg-event-db
 :save
 (fn [db [_ value]]
   (rf/dispatch [:saved value])
   db))"
                        "src/demo/events.cljs")]
      (is (contains? (set ids) :re-frame/reg-event-db-side-effect))))
  (testing "reg-sub signal functions are reported"
    (let [ids (rule-ids "(ns demo.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub
 :visible
 (fn [_] [:items])
 (fn [items _] items))"
                        "src/demo/subs.cljs")]
      (is (contains? (set ids) :re-frame/reg-sub-signals-shape))))
  (testing "mixed re-frame registration namespaces are reported"
    (let [ids (rule-ids "(ns demo.state
  (:require [re-frame.core :as rf]))

(rf/reg-event-db :save (fn [db _] db))
(rf/reg-sub :items (fn [db _] (:items db)))"
                        "src/demo/state.cljs")]
      (is (contains? (set ids) :re-frame/mixed-registration-namespace)))))

(deftest emits-hsx-contracts
  (testing "useEffect without dependency decision is reported"
    (let [ids (rule-ids "(ns demo.hsx
  (:require [io.factorhouse.hsx.core :as hsx]
            [\"react\" :as react]))

(defn panel []
  (react/useEffect (fn [] (js/console.log \"mounted\")))
  (hsx/create-element [:div]))"
                        "src/demo/hsx.cljs")]
      (is (contains? (set ids) :hsx/react-hook-deps))))
  (testing "inline anonymous HSX components are reported"
    (let [ids (rule-ids "(ns demo.hsx
  (:require [io.factorhouse.hsx.core :as hsx]))

(hsx/create-element (fn [] [:div]))"
                        "src/demo/hsx.cljs")]
      (is (contains? (set ids) :hsx/inline-component-identity)))))

(deftest emits-http-and-reitit-contracts
  (testing "raw Ring response maps are reported"
    (let [ids (rule-ids "(ns demo.handler
  (:require [ring.util.response :as response]))

(defn handler [_]
  {:status 200 :body \"ok\"})"
                        "src/demo/handler.clj")]
      (is (contains? (set ids) :ring/raw-response-map))))
  (testing "Ring middleware shape is reported"
    (let [ids (rule-ids "(ns demo.middleware
  (:require [ring.util.response :as response]))

(defn wrap-auth [handler]
  (fn [request]
    (handler request)))"
                        "src/demo/middleware.clj")]
      (is (contains? (set ids) :ring/middleware-shape))))
  (testing "inline Reitit route handlers are reported"
    (let [ids (rule-ids "(ns demo.routes
  (:require [reitit.ring :as ring]))

(def routes
  [[\"/health\" {:get (fn [_] {:status 200 :body \"ok\"})}]])"
                        "src/demo/routes.clj")]
      (is (contains? (set ids) :reitit/inline-route-handler))))
  (testing "Reitit middleware and coercion placement is reported"
    (let [ids (rule-ids "(ns demo.routes
  (:require [reitit.ring :as ring]))

(def route-data
  {:middleware [wrap-auth]
   :coercion coercion})"
                        "src/demo/routes.clj")]
      (is (contains? (set ids) :reitit/middleware-coercion-placement))))
  (testing "frontend routing plus RFX dispatch boundary is reported"
    (let [ids (rule-ids "(ns demo.router
  (:require [reitit.frontend :as rfro]
            [io.factorhouse.rfx.core :as rfx]))

(defn navigate! [event]
  (rfx/dispatch event))"
                        "src/demo/router.cljs")]
      (is (contains? (set ids) :reitit/frontend-rfx-dispatch-boundary)))))

(deftest emits-integrant-contracts
  (testing "init-key without matching halt-key is reported"
    (let [ids (rule-ids "(ns demo.system
  (:require [integrant.core :as ig]))

(defmethod ig/init-key ::db [_ config]
  (start-db config))"
                        "src/demo/system.clj")]
      (is (contains? (set ids) :integrant/init-halt-symmetry))))
  (testing "unqualified Integrant keys are reported for alignment"
    (let [ids (rule-ids "(ns demo.system
  (:require [integrant.core :as ig]))

(defmethod ig/halt-key! :db [_ db] (stop db))
(defmethod ig/init-key :db [_ config]
  (start-db config))"
                        "src/demo/system.clj")]
      (is (contains? (set ids) :integrant/config-key-namespace-alignment))))
  (testing "Integrant refs and expand-key are reported"
    (let [ids (rule-ids "(ns demo.system
  (:require [integrant.core :as ig]))

(def config {:app/server {:db (ig/ref :app/db)}})
(defmethod ig/expand-key ::module [_ opts]
  {:app/db opts})"
                        "src/demo/system.clj")]
      (is (contains? (set ids) :integrant/ref-graph-boundary))
      (is (contains? (set ids) :integrant/module-expansion-shape)))))

(deftest emits-malli-and-schema-contracts
  (testing "large inline Malli schemas are reported"
    (let [ids (rule-ids "(ns demo.schema
  (:require [malli.core :as m]))

(def user-schema
  [:map [:id int?] [:name string?] [:email string?]])"
                        "src/demo/schema.clj")]
      (is (contains? (set ids) :malli/large-inline-schema))))
  (testing "Malli function schemas are reported"
    (let [ids (rule-ids "(ns demo.schema
  (:require [malli.core :as m]))

(m/=> #'create-user [:=> [:cat :map] :map])"
                        "src/demo/schema.clj")]
      (is (contains? (set ids) :malli/function-schema-instrumentation))))
  (testing "Malli registries and refs are reported"
    (let [ids (rule-ids "(ns demo.schema
  (:require [malli.core :as m]))

(def registry {:registry {::user [:map [:friend [:ref ::user]]]}})"
                        "src/demo/schema.clj")]
      (is (contains? (set ids) :malli/registry-recursive-reference))))
  (testing "schema boundary validation and Reitit/Malli coercion are reported"
    (let [ids (rule-ids "(ns demo.api
  (:require [malli.core :as m]
            [reitit.ring :as ring]
            [ring.util.response :as response]))

(def route-data
  {:parameters {:body [:map [:id int?]]}})

(defn user-handler [request]
  (m/validate [:map [:id int?]] (:body request))
  {:status 200 :body \"ok\"})"
                        "src/demo/api.clj")]
      (is (contains? (set ids) :schema/boundary-validation))
      (is (contains? (set ids) :reitit/malli-coercion-integration))
      (is (contains? (set ids) :http/schema-boundary-coupling)))))

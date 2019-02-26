(ns hyperfiddle.foundation
  (:require
    [cats.monad.either :as either]
    #?(:cljs [contrib.reactive :as r])
    [hypercrud.types.Err :as Err]
    [hyperfiddle.domain :as domain]
    [hyperfiddle.project :as project]
    [hyperfiddle.runtime :as runtime]
    [hyperfiddle.security.domains]
    #?(:cljs [hyperfiddle.ui.iframe :refer [iframe-cmp]])
    #?(:cljs [hyperfiddle.ui.staging :as staging])
    [taoensso.timbre :as timbre]))


(def root-branch ["root"])

(defn ^:deprecated route-encode [rt route]
  (timbre/warn "foundation/route-encode is deprecated")
  (domain/url-encode (runtime/domain rt) route))

#?(:cljs
   (defn ^:deprecated stateless-login-url [& args]
     (timbre/warn "foundation/stateless-login-url is deprecated")
     (apply hyperfiddle.ide/stateless-login-url args)))

#?(:cljs
   (defn error-cmp [e]
     [:div
      [:h1 "Fatal error"]
      (if (Err/Err? e)
        [:div
         [:h3 (:msg e)]
         (when-let [data (:data e)]
           [:pre data])]
        [:div
         [:fieldset [:legend "(pr-str e)"]
          [:pre (pr-str e)]]
         [:fieldset [:legend "(ex-data e)"]                 ; network error
          [:pre (str (:data e))]]                           ; includes :body key
         [:fieldset [:legend "(.-stack e)"]                 ; network error
          [:pre (.-stack e)]]])]))

#?(:cljs
   (defn- error-cmp-with-stage [ctx e]
     (let [selected-dbname (r/atom nil)]
       (fn [ctx e]
         [:<>
          [error-cmp e]
          [staging/editor-cmp selected-dbname ctx]]))))

#?(:cljs
   (defn view [ctx]
     (if-let [e (or @(runtime/state (:peer ctx) [::runtime/fatal-error])
                    (some-> @(runtime/state (:peer ctx) [::runtime/partitions (:branch ctx) :error])))]
       [error-cmp-with-stage ctx e]
       [:<>
        [:style {:dangerouslySetInnerHTML {:__html @(runtime/state (:peer ctx) [::runtime/partitions (:branch ctx) :project :project/css])}}]
        (either/branch
          (project/eval-domain-code!+ @(runtime/state (:peer ctx) [::runtime/partitions (:branch ctx) :project :project/code]))
          (fn [e] [:div [:h2 {:style {:margin-top "10%" :text-align "center"}} "Misconfigured domain"]])
          (fn [_] [iframe-cmp ctx {:route @(runtime/state (:peer ctx) [::runtime/partitions (:branch ctx) :route])}]))])))

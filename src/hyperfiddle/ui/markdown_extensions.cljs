(ns hyperfiddle.ui.markdown-extensions
  (:require
    [contrib.data :refer [unwrap]]
    [contrib.string :refer [memoized-safe-read-edn-string blank->nil or-str]]
    [contrib.reagent :refer [fragment]]
    [contrib.ui.remark :as remark]
    [cuerdas.core :as str]
    [goog.object]
    [hyperfiddle.eval :refer [read-eval-with-bindings]]))


(defn a [content argument props ctx]
  [:a (merge {:href argument} (dissoc props :children))
   ; backwards compat with vanilla markdown anchors
   (or (:children props) content)])

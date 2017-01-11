(ns hypercrud.client.http
  (:require [cljs.pprint :as pprint]
            [cljs.reader :as reader]
            [clojure.set :as set]
            [goog.Uri]
            [hypercrud.client.core :as hc]
            [hypercrud.client.graph :as graph]
            [hypercrud.client.internal :as internal]
            [hypercrud.types :refer [->DbId]]
            [kvlt.core :as kvlt]
            [kvlt.middleware.params]
            [promesa.core :as p]))


(def content-type-transit "application/transit+json;charset=UTF-8")
(def content-type-edn "application/edn;charset=UTF-8")


(defmethod kvlt.middleware.params/coerce-form-params (keyword content-type-transit) [{:keys [form-params]}]
  (internal/transit-encode form-params))


(defmethod kvlt.middleware/from-content-type (keyword content-type-transit) [resp]
  (let [decoded-val (internal/transit-decode (:body resp))]
    (assoc resp :body decoded-val)))


(defmethod kvlt.middleware.params/coerce-form-params (keyword content-type-edn) [{:keys [form-params]}]
  (binding [pprint/*print-miser-width* nil
            pprint/*print-right-margin* 200]
    (with-out-str (pprint/pprint form-params))))


(defmethod kvlt.middleware/from-content-type (keyword content-type-edn) [resp]
  (let [decoded-val (reader/read-string (:body resp))]
    (assoc resp :body decoded-val)))


(defn resolve-relative-uri [^goog.Uri entry-uri ^goog.Uri relative-uri]
  (-> (.clone entry-uri)
      (.resolve relative-uri)))


; graph is always assumed to be touched
(deftype Client [entry-uri ^:mutable super-graph]
  hc/Client
  (hydrate! [this named-queries force? staged-tx editor-dbval editor-schema]
    (if (and (not force?) (hc/hydrated? this named-queries))
      (p/resolved super-graph)
      (-> (kvlt/request!
            {:url (resolve-relative-uri entry-uri (goog.Uri. "hydrate"))
             :content-type content-type-transit             ; helps debugging to view as edn
             :accept content-type-transit                   ; needs to be fast so transit
             :method :post
             :form {:staged-tx staged-tx
                    :queries (into [] (vals named-queries))}
             :as :auto})
          (p/then (fn [resp]
                    (let [new-graph (graph/->SuperGraph named-queries {} nil)
                          {:keys [t pulled-trees-map tempids]} (-> resp :body :hypercrud)]
                      (graph/set-state! new-graph editor-dbval editor-schema pulled-trees-map tempids)
                      (set! super-graph new-graph)
                      super-graph))))))


  (hydrated? [this named-queries]
    ; compare our pre-loaded state with the graph dependencies
    (set/subset? (set named-queries) (some-> super-graph .-named-queries set)))


  (transact! [this tx]
    (-> (kvlt/request!
          {:url (resolve-relative-uri entry-uri (goog.Uri. "transact"))
           :content-type content-type-edn
           :accept content-type-edn
           :method :post
           :form tx
           :as :auto})
        (p/then (fn [resp]
                  (if (:success resp)
                    (p/resolved (-> resp :body :hypercrud))
                    (p/rejected resp)))))))

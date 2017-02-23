(ns hypercrud.browser.system-links
  (:require [hypercrud.browser.links :as links]
            [hypercrud.client.core :as hc]
            [hypercrud.types :refer [->DbId ->DbVal ->EntityRequest]]))


(defn system-link? [link-dbid]
  (map? (:id link-dbid)))


(defn system-link-id->parent-link-dbid [system-link-dbid]
  (->DbId (get system-link-dbid :parent-link-id)
          (get system-link-dbid :parent-link-conn-id)))


(defn system-edit-link-dbid [entity-conn-id link-name parent-link-dbid]
  (->DbId {:parent-link-id (-> parent-link-dbid :id)
           :parent-link-conn-id (-> parent-link-dbid :conn-id)
           :link/ident :system-edit
           :link/name link-name
           :entity-conn-id entity-conn-id}
          (-> parent-link-dbid :conn-id)))


(defn system-edit-field-link-dbid [find-element-id field-id parent-link-dbid]
  (->DbId {:parent-link-id (-> parent-link-dbid :id)
           :parent-link-conn-id (-> parent-link-dbid :conn-id)
           :link/ident :system-edit-field
           ;find-element-id
           ;field-id
           }
          (-> parent-link-dbid :conn-id)))


(defn system-edit-link [connection-dbid link-name parent-link]
  {:db/id (system-edit-link-dbid (:id connection-dbid) link-name (:db/id parent-link))
   :link/name link-name
   :hypercrud/owner (:hypercrud/owner parent-link)
   :link/request {:link-entity/connection {:db/id connection-dbid}}})


(defn system-edit-field-link [find-element field parent-link]
  {:db/id (system-edit-field-link-dbid (-> find-element :db/id :id) (get-in field [:db/id :id]) (:db/id parent-link))
   :link/name (str "edit " (:find-element/name find-element) " " (get-in field [:field/attribute :attribute/ident]))
   :hypercrud/owner (:hypercrud/owner parent-link)
   :link/request {:link-entity/connection (:find-element/connection find-element)
                  :link-entity/form (:find-element/form find-element)}})


(defn overlay-system-links-tx
  "provide the system links (edit, new, remove). sub-queries (e.g. combo boxes) will get the old pulled-tree.
  Since we only changed link, this is only interesting for the hc-in-hc case.

  todo don't overlay system links on system links"
  [parent-link]
  (case (links/link-type parent-link)
    :link-query
    (let [find-elements (get-in parent-link [:link/request :link-query/find-element])
          edit-links (->> find-elements
                          (mapv (fn [find-element]
                                  (let [connection-dbid (-> find-element :find-element/connection :db/id)
                                        link-name (str "system-edit " (:find-element/name find-element))]
                                    {:anchor/prompt (str "edit " (:find-element/name find-element))
                                     :anchor/link (system-edit-link connection-dbid link-name parent-link)
                                     :anchor/repeating? true
                                     :anchor/find-element find-element
                                     :anchor/formula (pr-str {:entity-dbid-s (pr-str `(fn [~'ctx]
                                                                                        (get-in ~'ctx [:result ~(:find-element/name find-element) :db/id])))})})
                                  #_(->> (-> find-element :find-element/form :form/field)
                                         (filter #(= :db.type/ref (-> % :field/attribute :attribute/valueType)))
                                         (mapcat (fn [field]
                                                   [{:db/id "i dont think we need this"
                                                     :anchor/prompt "edit"
                                                     :anchor/link (system-edit-field-link find-element field parent-link)
                                                     :anchor/repeating? true
                                                     :anchor/find-element find-element
                                                     :anchor/attribute ??? #_ field
                                                     :anchor/formula (pr-str {:entity-dbid-s (pr-str '(fn [ctx] nil))})}]))))))
          create-links (->> find-elements
                            (mapv :find-element/connection)
                            (set)
                            (mapv (fn [connection]
                                    (let [connection-dbid (:db/id connection)
                                          link-name (str "system-create " (:database/ident connection))]
                                      {:anchor/prompt (str "create in " (:database/ident connection))
                                       :anchor/link (system-edit-link connection-dbid link-name parent-link)
                                       :anchor/repeating? false
                                       :anchor/formula (pr-str {:entity-dbid-s (pr-str `(fn [~'ctx]
                                                                                          (hc/*temp-id!* ~(:id connection-dbid))))})}))))
          anchors (concat edit-links create-links)]
      (update parent-link :link/anchor concat anchors))

    :link-entity parent-link                                ; No system links yet for entity links.
    parent-link))


(defn request-for-system-link [system-link-id]
  (let [parent-link-dbid (system-link-id->parent-link-dbid system-link-id)]
    (->EntityRequest parent-link-dbid
                     (->DbVal hc/*root-conn-id* nil)
                     (let [form-pull-exp ['* {:form/field
                                              ['*
                                               {:field/attribute ['*
                                                                  {:attribute/valueType [:db/id :db/ident]}
                                                                  {:attribute/cardinality [:db/id :db/ident]}
                                                                  {:attribute/unique [:db/id :db/ident]}]}]}]]
                       [:db/id
                        {:hypercrud/owner ['*]
                         :link/request
                         [:db/id
                          {:link-query/find-element [:db/id
                                                     :find-element/name
                                                     :find-element/connection
                                                     {:find-element/form form-pull-exp}]}]}]))))


(defn generate-system-link [system-link-id system-link-deps]
  (let [{:keys [entity-conn-id link-name]} system-link-id
        entity-conn-dbid (->DbId entity-conn-id hc/*root-conn-id*)
        ; system links are only generated on QueryRequests, so we don't need to determine the type of the parent link
        parent-link system-link-deps]
    (case (:link/ident system-link-id)
      :system-edit (system-edit-link entity-conn-dbid link-name parent-link))))

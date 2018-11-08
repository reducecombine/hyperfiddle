(ns contrib.validation-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [deftest is]]
    [contrib.data :refer [collect]]
    [contrib.datomic]
    [contrib.validation :refer [explained-for-view form-validation-hints]]))


(def result [{:foo/bar 1 :db/id 123}
             {:foo/baz 42 :db/id 124}])
(def e (s/explain-data (s/coll-of (s/keys :req [:foo/bar])) result))

(require '[hyperfiddle.data])
(-> (explained-for-view contrib.datomic/smart-lookup-ref-no-tempids e)
    ::s/problems
    form-validation-hints)

(def result {:db/id 17592186061847,
             :fiddle/renderer
             "hyperfiddle.ide.fiddles.fiddle-src/fiddle-src-renderer",
             :fiddle/links
             [{:db/id 17592186061848,
               :link/class [:fiddle],
               :link/rel :hf/remove}
              {:db/id 17592186061849,
               :link/class [:link],
               :link/path ":fiddle/links",
               :link/rel :hf/remove}
              {:db/id 17592186061850,
               :link/class [:fiddle-options],
               :link/fiddle
               {:db/id 17592186045605,
                :fiddle/ident
                :hyperfiddle.ide/fiddle-options,
                :fiddle/query
                "[:find (pull ?link [:db/id :fiddle/ident])\n :where (or [?link :fiddle/ident] [?link :fiddle/type])]",
                :fiddle/type :query},
               :link/rel :hf/iframe}
              {:db/id 17592186061851,
               :link/class [:fiddle],
               :link/fiddle
               {:db/id 17592186056398,
                :fiddle/ident :hyperfiddle.ide/new-fiddle,
                :fiddle/type :entity},
               :link/path ":fiddle/links :link/fiddle",
               :link/rel :hf/affix}
              {:db/id 17592186061852,
               :link/class [:link],
               :link/fiddle
               {:db/id 17592186058175,
                :fiddle/ident :hyperfiddle.ide/new-link,
                :fiddle/type :entity},
               :link/path ":fiddle/links",
               :link/rel :hf/affix1}],
             :fiddle/type :entity,
             :fiddle/pull
             "; copied from hypercrud.browser.base/meta-pull-exp-for-link\n[:db/id\n :db/doc\n :fiddle/css\n :fiddle/ident\n {:fiddle/links [:db/id\n                 :link/class\n                 {:link/fiddle [:db/id\n                                :fiddle/ident               ; routing\n                                :fiddle/query               ; validation\n                                :fiddle/type                ; validation\n                                ]}\n                 :link/formula\n                 :link/path\n                 :link/rel\n                 :link/tx-fn]}\n :fiddle/markdown\n :fiddle/pull\n :fiddle/pull-database\n :fiddle/query\n :fiddle/cljs-ns\n :fiddle/renderer\n :fiddle/type\n :fiddle/hydrate-result-as-fiddle\n *                                                          ; For hyperblog, so we can access :hyperblog.post/title etc from the fiddle renderer\n ]",
             :fiddle/css
             "table.hyperfiddle.-fiddle-links { table-layout: fixed; }\ntable.-fiddle-links th.-link-formula,\ntable.-fiddle-links th.-link-tx-fn { width: 40px; }\ntable.-fiddle-links th.-hypercrud-browser-path--fiddle-links { width: 60px; }\n\ntable.-fiddle-links td.-hypercrud-browser-path--fiddle-links--link-fiddle { display: flex; }\ntable.hyperfiddle.-fiddle-links td.field.-link-fiddle > select { flex: 0 1 80% !important; } /* line up :new */\n",
             :fiddle/ident :hyperfiddle/ide})
(def e (s/explain-data :hyperfiddle/ide result))
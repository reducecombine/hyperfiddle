(ns hyperfiddle.ide
  (:require [clojure.string :as str]
            [contrib.css :refer [classes]]
            [contrib.data :refer [unwrap]]
            [contrib.reactive :as r]
    #?(:cljs [contrib.reagent :refer [fragment]])
            [contrib.rfc3986 :refer [split-fragment]]
            [contrib.string :refer [safe-read-edn-string empty->nil]]
            [hypercrud.browser.base :as base]
    #?(:cljs [hypercrud.browser.browser-ui :as browser-ui])
            [hypercrud.browser.context :as context]
            [hypercrud.browser.core :as browser]
            [hypercrud.browser.routing :as routing]
            [hypercrud.browser.router :as router]
            [hypercrud.browser.router-bidi :as router-bidi]
            [hypercrud.browser.system-fiddle :refer [system-fiddle?]]
    #?(:cljs [hypercrud.ui.error :as ui-error])
    #?(:cljs [hypercrud.ui.navigate-cmp :as navigate-cmp])
    #?(:cljs [hypercrud.ui.stale :as stale])
            [hyperfiddle.foundation :as foundation]
            [hyperfiddle.io.hydrate-requests :refer [hydrate-one!]]
            [hyperfiddle.runtime :as runtime]
            [promesa.core :as p]
            [taoensso.timbre :as timbre]

    ; pull in the entire ide app for reference from user-land
            [hyperfiddle.ide.actions]
    #?(:cljs [hyperfiddle.ide.fiddles.domain])
            [hyperfiddle.ide.fiddles.fiddle-links.bindings]
    #?(:cljs [hyperfiddle.ide.fiddles.fiddle-links.renderer])
    #?(:cljs [hyperfiddle.ide.fiddles.fiddle-src :refer [fiddle-src-renderer]])
            [hyperfiddle.ide.fiddles.schema]
    #?(:cljs [hyperfiddle.ide.fiddles.schema-attribute])
    #?(:cljs [hyperfiddle.ide.fiddles.topnav :as topnav])
    #?(:cljs [hyperfiddle.ide.fiddles.user-dashboard])))

(defn domain [rt hyperfiddle-hostname hostname]
  (let [domain-basis (if-let [global-basis @(runtime/state rt [::runtime/global-basis])]
                       (:domain global-basis)
                       (->> @(runtime/state rt [::runtime/partitions])
                            (some (fn [[_ partition]]
                                    (->> (:local-basis partition)
                                         (filter (fn [[k _]] (= foundation/domain-uri k)))
                                         seq)))))
        stage nil
        hf-domain-name (foundation/hostname->hf-domain-name hostname hyperfiddle-hostname)
        request (foundation/domain-request hf-domain-name rt)]
    (-> (hydrate-one! rt (into {} domain-basis) stage request)
        (p/then (fn [domain]
                  (if (nil? (:db/id domain))
                    ; terminate when domain not found
                    (throw (ex-info "Domain does not exist" {:hyperfiddle.io/http-status-code 404
                                                             :domain-name hf-domain-name}))
                    domain))))))

(defn ide-route [[fiddle datomic-args service-args frag :as route]]
  ; Don't impact the request! Topnav can always use :target-route
  [:hyperfiddle/topnav [#entity["$" (base/legacy-fiddle-ident->lookup-ref fiddle)]]])

; ide is overloaded, these ide-context functions are exclusive to (top)
; despite being in the namespace (hyperfiddle.ide) which encompasses the union of target/user (bottom) and ide (top)
(defn- *-ide-context [ctx]
  (-> ctx
      (assoc :hypercrud.ui/display-mode (r/track identity :user)
             :target-domain (:hypercrud.browser/domain ctx) ; todo rename :target-domain to :hyperfiddle.ide/target-domain
             :user-profile @(runtime/state (:peer ctx) [:user-profile]))
      (context/source-mode)))

(defn leaf-ide-context [ctx]
  ; ide leaf-context does not have enough information to set hyperfiddle.ide/target-route
  ; this means ide popovers CANNOT access it
  (*-ide-context ctx))

(defn page-ide-context [ctx target-route]
  {:pre [target-route]}
  (-> (assoc ctx
        ::runtime/branch-aux {::foo "ide"}
        ; hyperfiddle.ide/target-route is ONLY available to inlined IDE (no deferred popovers)
        :target-route target-route)                         ; todo rename :target-route to :hyperfiddle.ide/target-route
      (*-ide-context)))

(defn activate-ide? [x]
  ; To userland this "www" constant would require more complicated rules
  ; for mapping a hostname to a domain-ident
  (let [should-be-active (and (not (foundation/alias? x))
                              #_(not= "www" x))
        ; WWW needs a way to activate this. For now we can mirror the domain on www2
        explicitly-active false]
    (or explicitly-active should-be-active)))

(defn- *-target-context [ctx route]
  (assoc ctx
    :hypercrud.browser/page-on-click (let [branch-aux {:hyperfiddle.ide/foo "page"}]
                                       #?(:cljs (r/partial browser-ui/page-on-click (:peer ctx) nil branch-aux)))
    :hypercrud.ui/display-mode (runtime/state (:peer ctx) [:display-mode])
    :ide-active (activate-ide? (foundation/hostname->hf-domain-name ctx))
    :user-profile @(runtime/state (:peer ctx) [:user-profile])

    ; these target values only exists to allow the topnav to render in the bottom/user
    ; IF we MUST to support that, this should probably be done higher up for both ide and user at the same time
    ; and SHOULD ONLY be applied for ide within ide (other user fns don't get access to these values)
    :target-domain (:hypercrud.browser/domain ctx)          ; todo rename :target-domain to :hyperfiddle.ide/target-domain
    :target-route route                                     ; todo rename :target-route to :hyperfiddle.ide/target-route
    ))

(defn leaf-target-context [ctx route]
  (*-target-context ctx route))

(defn page-target-context [ctx route]
  (-> (assoc ctx ::runtime/branch-aux {::foo "user"})
      (*-target-context route)))

(defn route-decode [rt path-and-frag]
  {:pre [(string? path-and-frag)]}
  (let [[path frag] (split-fragment path-and-frag)
        domain @(runtime/state rt [:hyperfiddle.runtime/domain])
        home-route (some-> domain :domain/home-route safe-read-edn-string unwrap) ; in hf format
        router (some-> domain :domain/router safe-read-edn-string unwrap)]

    (or
      (case path
        "/" (if home-route (router/assoc-frag home-route frag))
        (or (if (= "/_/" (subs path-and-frag 0 3)) (routing/decode (subs path-and-frag 2) #_"include leading /"))
            (if router (router-bidi/decode router path-and-frag))
            (routing/decode path-and-frag)))
      [:hyperfiddle.system/not-found])))

(defn route-encode [rt [fiddle _ _ frag :as route]]
  {:post [(str/starts-with? % "/")]}
  (let [domain @(runtime/state rt [:hyperfiddle.runtime/domain])
        router (some-> domain :domain/router safe-read-edn-string unwrap)
        home-route (some-> domain :domain/home-route safe-read-edn-string unwrap)
        home-route (if router (router-bidi/bidi->hf home-route) home-route)]
    (or
      (if (system-fiddle? fiddle) (str "/_" (routing/encode route)))
      (if (= (router/dissoc-frag route) home-route) (if (empty->nil frag) (str "/#" frag) "/"))
      (if router (router-bidi/encode router route))
      (routing/encode route))))

(defn local-basis [global-basis route ctx]
  ;local-basis-ide and local-basis-user
  (let [{:keys [ide user]} global-basis
        basis (case (get-in ctx [::runtime/branch-aux ::foo])
                "page" (concat ide user)
                "ide" (concat ide user)
                "user" user)
        basis (sort basis)]                                 ; Userland api-fn should filter irrelevant routes
    (timbre/debug (pr-str basis))
    #_(determine-local-basis (hydrate-route route ...))
    basis))

; Reactive pattern obfuscates params
(defn api [route ctx]
  {:pre [route (not (string? route))]}
  (case (get-in ctx [::runtime/branch-aux ::foo])
    "page" (into (browser/request-from-route route (page-target-context ctx route))
                 (when true #_(activate-ide? (foundation/hostname->hf-domain-name ctx))
                   (browser/request-from-route (ide-route route) (page-ide-context ctx route))))
    "ide" (browser/request-from-route route (leaf-ide-context ctx))
    "user" (browser/request-from-route route (leaf-target-context ctx route))))

#?(:cljs
   (defn view-page [route ctx]
     (let [dev (activate-ide? (foundation/hostname->hf-domain-name ctx))
           src-mode (let [[_ _ _ frag] route] (topnav/src-mode? frag)) ; Immoral - :src bit is tunneled in userland fragment space
           ctx (assoc ctx :navigate-cmp (r/partial navigate-cmp/navigate-cmp (r/partial runtime/encode-route (:peer ctx))))
           ide-ctx (page-ide-context ctx route)]
       (fragment                                            ; These are the same data, just different views.
         :view-page
         (when dev
           [browser/ui-from-route (ide-route route)
            (assoc ide-ctx :hypercrud.ui/error (r/constantly ui-error/error-inline)
                           #_#_:user-renderer hyperfiddle.ide.fiddles.topnav/renderer)
            "topnav hidden-print"])

         (when (and dev src-mode)
           [browser/ui-from-route (ide-route route)
            (assoc ide-ctx :user-renderer fiddle-src-renderer)
            "devsrc"])

         (when-not src-mode
           (let [ctx (page-target-context ctx route)]
             [browser/ui-from-route route ctx (classes (some-> ctx :hypercrud.ui/display-mode deref name)
                                                       "hyperfiddle-user"
                                                       (if dev "hyperfiddle-ide-user"))]))))))

#?(:cljs
   (defn view [route ctx]                                   ; pass most as ref for reactions
     (case (get-in ctx [::runtime/branch-aux ::foo])
       "page" (view-page route ctx)                         ; component, seq-component or nil
       ; On SSR side this is only ever called as "page", but it could be differently (e.g. turbolinks)
       ; On Browser side, also only ever called as "page", but it could be configured differently (client side render the ide, server render userland...?)
       "ide" [browser/ui-from-route route (leaf-ide-context ctx)]
       "user" [browser/ui-from-route route (leaf-target-context ctx route)])))

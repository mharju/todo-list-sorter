(ns todo.auth
  (:require [clojure.string :as str]
            [cljs.core.async :as async :refer [go]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [goog.object :as gobj]
            ["@azure/msal-browser" :as msal]
            ["@microsoft/microsoft-graph-client" :as gc]
            [todo.config :refer [config]]))

(def ^:private client-application (msal/PublicClientApplication.
                                    (clj->js {:auth (select-keys config [:redirectUri :clientId :authority])
                                              :cache {:cacheLocation          "sessionStorage"
                                                      :storeAuthStateInCookie true}})))
(defn interaction-required? [error]
  (let [message (.-message error)]
    (if (str/blank? message)
      false
      (or
        (str/includes? message "consent_required")
        (str/includes? message "interaction_required")
        (str/includes? message "login_required")
        (str/includes? message "no_account_in_silent_request")))))

(defn get-access-token [scopes]
  (go
    (let [[account] (js->clj (.getAllAccounts client-application))]
      (try
        (when-not (some? account)
          (throw (js/Error. "login_required")))
        (let [settings {:scopes scopes :account account}
              result (<p! (.acquireTokenSilent client-application (clj->js settings)))]
          (gobj/get result "accessToken"))
        (catch js/Error e
          (if (interaction-required? e)
            (let [result (<p! (.acquireTokenPopup client-application #js {:scopes scopes}))]
              (gobj/get result "accessToken"))
            (throw e)))))))

(defn graph-client [token]
  (gc/Client.init (clj->js {:defaultVersion "beta" :authProvider (fn [done] (done nil token))})))

(declare get-access-token)
(defn graph-client-authorized []
  (go
    (graph-client (async/<! (get-access-token (clj->js (:scopes config)))))))

(defn get-user-details [token]
  (go
    (let [client (graph-client token)]
      (<p! (-> client
               (.api "/me")
               (.select "displayName,mail,userPrincipalName")
               (.get))))))

(defn get-user-profile []
  (go 
    (let [token (async/<! (get-access-token (clj->js (:scopes config))))]
      (async/<! (get-user-details token)))))

(defn login []
  (go 
    (<p! (.loginPopup client-application (clj->js {:scopes (:scopes config) :prompt "select_account"})))
    (async/<! (get-user-profile))))

(defn logout []
  (.logout client-application))

(comment
  (go
   (let [token (async/<! (login))]
     (js/console.info "Got me user profile" token))))

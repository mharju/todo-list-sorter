(ns todo.tasks
  (:require [cljs.core.async :as async :refer [go go-loop]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [re-frame.core :as re]
            [goog.object :as gobj]
            [todo.auth :as auth]
            [todo.config :as config]
            ["date-fns" :as dfn]))

(defn tasks [id {:keys [skip]}]
  (go
    (let [client (async/<! (auth/graph-client-authorized))
          result (<p! (-> client
                          (.api (cond->
                                  (str "/me/outlook/taskFolders/" id "/tasks")
                                  skip (str "?$skip=" skip)))
                          (.get)))
          next-link (gobj/get result "@odata.nextLink")]
      {:value (-> (gobj/get result "value")
                  (js->clj :keywordize-keys true))
       :next-link next-link})))

(defn fetch-all [id {max-items :max}]
  (let [progress-ch (async/chan)]
    [progress-ch 
     (go-loop [skip 0 items []]
      (async/>! progress-ch (count items))
      (let [{:keys [value next-link]} (async/<! (tasks id {:skip skip}))]
        (if (and next-link (< (+ 10 (count items)) max-items))
          (recur (+ skip 10) (into items value))
          items)))]))

(defonce all-tasks (atom []))

(defn visits [tasks]
  (->> tasks 
       (filter (comp (partial = "completed") :status))
       (sort-by (comp dfn/startOfDay dfn/parseJSON :dateTime :completedDateTime))
       (group-by (comp dfn/startOfDay dfn/parseJSON :dateTime :completedDateTime))))

(defn load-grocery-list [{:keys [progress-dispatch complete-dispatch error-dispatch]}]
  (if (seq @all-tasks)
    ;; return from cache 
    (re/dispatch (conj complete-dispatch (visits @all-tasks)))
    (let [[progress-ch tasks-ch] (fetch-all (:groceryListId config/config) {:max 50})]
      (go-loop []
        (let [[data ch] (async/alts! [progress-ch tasks-ch])]
          (if (= ch tasks-ch)
            (do 
              (reset! all-tasks data)
              (re/dispatch (conj complete-dispatch (visits data))))
            (do 
              (re/dispatch (conj progress-dispatch data))
              (recur))))))))
(comment
  [:changeKey :isReminderOn :parentFolderId :sensitivity :lastModifiedDateTime :completedDateTime :hasAttachments :dueDateTime :reminderDateTime :categories :status :id :startDateTime :recurrence :createdDateTime :body :subject :owner :assignedTo :importance]
  (count @all-tasks)
  (->> 
    @all-tasks
    (take 10)
    (mapv #(select-keys % [:subject])))
  (->> (visits @all-tasks)
       (map (fn [v] 
              (->> v 
                   (map #(select-keys % [:subject])))))
       js/console.log))

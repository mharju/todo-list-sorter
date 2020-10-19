(ns todo.core
  (:require [reagent.core :as r]
            [reagent.dom :as rd]
            [re-frame.core :as re]
            ["@material-ui/core/Box" :rename {default Box}]
            ["@material-ui/core/Container" :rename {default Container}]
            ["@material-ui/core/Grid" :rename {default Grid}]
            ["@material-ui/core/Button" :rename {default Button}]
            ["@material-ui/core/List" :rename {default List}]
            ["@material-ui/core/ListItem" :rename {default ListItem}]
            ["@material-ui/core/ListItemIcon" :rename {default ListItemIcon}]
            ["@material-ui/core/ListItemText" :rename {default ListItemText}]
            ["@material-ui/core/Typography" :rename {default Typography}]
            ["@material-ui/core/CircularProgress" :rename {default CircularProgress}]
            ["@material-ui/icons/Folder" :rename {default FolderIcon}]
            ["date-fns" :as dfn]
            ["react-beautiful-dnd" :as rbd]
            [todo.auth :as auth]
            [todo.tasks :as tasks]
            [todo.utils :as utils]))

(re/reg-fx ::get-all-visits
  (fn []
    (tasks/load-grocery-list {:progress-dispatch [::set-progress] 
                              :complete-dispatch [::set-visits]
                              :error-dispatch    [::error-handler]})))
(re/reg-event-fx ::init
  (fn [{:keys [db]}]
    {::get-all-visits nil}))
(re/reg-event-fx ::set-visits
  (fn [{:keys [db]} [_ visits]]
    {:db (-> db 
             (assoc ::visits visits)
             (dissoc ::progress))}))
(re/reg-event-db ::select-visit
 (fn [db [_ visit]]
   (assoc db ::selected-visit visit)))
(re/reg-event-db ::set-progress
 (fn [db [_ progress]]
   (assoc db ::progress progress)))
(re/reg-event-db ::move-item
 (fn [{::keys [selected-visit] :as db} [_ from to]]
   (update-in db [::visits selected-visit] utils/move from to)))
(re/reg-event-fx ::error-handler
 (fn [{:keys [db]} [_ error]]
   (js/console.info (.-message error))))
(re/reg-sub ::visits (fn [db] (::visits db)))
(re/reg-sub ::progress (fn [db] (::progress db)))
(re/reg-sub ::selected-visit (fn [db] (::selected-visit db)))
(re/reg-sub ::loading
  :<- [::visits]
  :<- [::progress]
  (fn [[visits progress]]
    (and (not (seq visits)) progress)))
(re/reg-sub ::visit-list
  :<- [::visits]
  (fn [visits]
    (->>
      (keys visits)
      (sort >))))
(re/reg-sub ::selected-visit-entry
  :<- [::visits]
  :<- [::selected-visit]
  (fn [[visits selected-visit]]
    (get visits selected-visit)))

(defn visit []
  (r/with-let [selected-visit (re/subscribe [::selected-visit-entry])
               loading (re/subscribe [::loading])
               progress (re/subscribe [::progress])]
    (if (some? @selected-visit)
      [:> rbd/DragDropContext
       {:onDragEnd (fn [data]
                     (let [data (js->clj data :keywordize-keys true)
                           from (get-in data [:source :index])
                           to (get-in data [:destination :index])]
                       (re/dispatch-sync [::move-item from to])))}
          [:> rbd/Droppable {:droppableId "task-list"}
            (fn [provided-raw] 
              (let [provided (js->clj provided-raw :keywordize-keys true)
                    props (merge {:ref (:innerRef provided)}
                                 (:droppableProps provided))
                    items (for [[index {:keys [subject]}] (map-indexed vector @selected-visit)]
                            ^{:key subject} 
                            [:> rbd/Draggable {:draggableId subject :index index}
                             (fn [provided]
                               (let [provided (js->clj provided :keywordize-keys true)]
                                 (r/as-element 
                                   [:> ListItem (merge 
                                                  {:ref (:innerRef provided)}
                                                  (:dragHandleProps provided) 
                                                  (:draggableProps provided))
                                    [:> ListItemText {:primary subject}]])))])]
                (r/as-element 
                  [:> List props items (.-placeholder provided-raw)])))]]
      [:> Box {:m 2}
       (if @loading 
         [:div {:style {:display :flex :flex-direction :row :align-items :center}}
          [:> CircularProgress {:style {:margin-right "1rem"}}]
          [:> Typography @progress " items loaded"]]
         [:> Typography "Select a visit from the list on the left."])])))

(defn app []
  (r/with-let [visits (re/subscribe [::visit-list])]
    [:> Grid {:container true :direction :row :style {:max-height "100vh" :overflow :hidden}}
     [:> Grid {:item true :xs 4 :style {:max-height "100vh" :overflow :scroll}}
      [:> List
       (for [at @visits]
         ^{:key at}
         [:> ListItem {:button true :onClick #(re/dispatch [::select-visit at])}
          [:> ListItemIcon [:> FolderIcon]]
          [:> ListItemText {:primary (str "Shopping " (dfn/formatDistanceToNow at) " ago")}]])]]
     [:> Grid {:item true :xs 8 :style {:max-height "100vh" :overflow :scroll}}
      [visit]]]))

(defn ^:dev/after-load render! []
  (re/clear-subscription-cache!)
  (rd/render [app] (js/document.getElementById "app")))

(defn main! []
  (re/dispatch [::init])
  (render!))

(comment
  (main!))

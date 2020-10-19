(ns todo.config
  (:require config))

(def config {:clientId      config/client-id
             :groceryListId config/grocery-list-id
             :redirectUri   "http://localhost:9500"
             :authority     "https://login.microsoftonline.com/consumers"
             :scopes        ["user.read"
                             "tasks.read"
                             "tasks.read.shared"
                             "tasks.readwrite.shared"
                             "tasks.readwrite"]})

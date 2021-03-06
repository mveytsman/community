(ns community.components.app
  (:require [community.api :as api]
            [community.models :as models]
            [community.routes :as routes]
            [community.location :as location]
            [community.components.shared :as shared]
            [community.util :refer-macros [<? p]]
            [community.partials :as partials]
            [om.core :as om]
            [sablono.core :refer-macros [html]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defmulti notification-summary :type)

(defmethod notification-summary "mention" [mention]
  (html
    [:div
     [:strong (-> mention :mentioned-by :name)]
     " mentioned you in "
     [:strong (-> mention :thread :title)]]))

(defmulti notification-link-to :type)

(defmethod notification-link-to "mention" [mention]
  (routes/routes :thread (:thread mention)))

(defn mark-as-read! [notification]
  (om/update! notification :read true)
  (api/mark-notification-as-read @notification))

(defn delete-notification!
  "Delete the i-th notification from the user's notifications."
  [user i]
  (let [notifications (:notifications @user)]
    (om/update! user :notifications
      (vec (concat (subvec notifications 0 i)
                   (subvec notifications (inc i) (count notifications)))))))

(defn notification-component [{:keys [notification on-click on-remove]} owner]
  (reify
    om/IDisplayName
    (display-name [_] "Notification")

    om/IDidMount
    (did-mount [_]
      (.tooltip (js/jQuery (om/get-node owner "remove-button"))))

    om/IRender
    (render [_]
      (html
        [:a.list-group-item
         {:href (notification-link-to notification)
          :onClick (fn [e]
                     (.preventDefault e)
                     (on-click e))}
         [:button.close.pull-right
          {:onClick (fn [e]
                      (.preventDefault e)
                      (on-remove e)
                      false)
           :data-toggle "tooltip"
           :data-placement "top"
           :title "Remove"
           :ref "remove-button"}
          "×"]
         [:div {:class (if (:read notification) "text-muted")}
          (notification-summary notification)]]))))

(defn notifications-component [user owner]
  (reify
    om/IDisplayName
    (display-name [_] "Notifications")

    om/IRender
    (render [_]
      (let [notifications (:notifications user)]
        (html
          [:div#notifications
           [:h3 "Notifications"]
           (if (empty? notifications)
             [:div "No new notifications"]
             [:div.list-group
              (for [[i n] (map-indexed vector notifications)]
                (om/build notification-component
                          {:notification n
                           :on-click #(do (mark-as-read! n)
                                          (location/redirect-to (notification-link-to @n)))
                           :on-remove #(do (mark-as-read! n)
                                           (delete-notification! user i))}))])])))))

(defn navbar-component [{:keys [current-user]} owner]
  (reify
    om/IDisplayName
    (display-name [_] "NavBar")

    om/IRender
    (render [this]
      (html
        [:nav.navbar.navbar-default {:role "navigation"}
         [:div.container
          [:div.navbar-header
           (partials/link-to "/" {:class "navbar-brand"} "Community")]
          [:ul.nav.navbar-nav.navbar-right
           [:li [:a {:href "https://github.com/hackerschool/community"} "Source"]]
           (when current-user
             [:li.dropdown
              [:a.dropdown-toggle {:href "#" :data-toggle "dropdown"}
               (:name current-user) [:b.caret]]
              [:ul.dropdown-menu
               [:li [:a {:href "/logout"} "Logout"]]]])]]]))))

(defn welcome-info-component [_ owner]
  (reify
    om/IDisplayName
    (welcome-info [this] "WelcomeInfo")

    om/IInitState
    (init-state [this]
      {:closed? false})

    om/IRenderState
    (render-state [this {:keys [closed?]}]
      (html
       (if closed?
         [:div]
         [:div.row
          [:div.col-lg-12
           [:div.alert.alert-info
            [:strong "Welcome! "]
            "As you can tell, Community is in very early stages. Expect things to change, threads and posts to be deleted, etc. Thanks for checking it out!"
            [:button.close {:onClick #(om/set-state! owner :closed? true)}
             "×"]]]])))))

(defn start-notifications-subscription! [user-id app]
  (when api/subscriptions-enabled?
    (go
      (let [[notifications-feed unsubscribe!] (api/subscribe! {:feed :notifications :id user-id})]
        (loop []
          (when-let [message (<! notifications-feed)]
            (om/transact! app [:current-user :notifications]
                          #(conj % (models/notification (:data message))))
            (recur)))))))

(defn app-component [{:as app :keys [current-user route-data errors]}
                     owner]
  (reify
    om/IDisplayName
    (display-name [_] "App")

    om/IDidMount
    (did-mount [this]
      (go
        (try
          (let [user (<? (api/current-user))]
            (if (= user :community.api/no-current-user)
              (set! (.-location js/document) "/login")
              (do (om/update! app :current-user user)
                  (start-notifications-subscription! (:id user) app))))

          (catch ExceptionInfo e
            ;; TODO: display an error modal
            (prn (ex-data e))))))

    om/IRender
    (render [this]
      (html
        [:div
         (om/build navbar-component app)
         [:div.container
          (om/build welcome-info-component nil)
          (when (not (empty? errors))
            [:div
             (for [error errors]
               [:div.alert.alert-danger error])])
          (if current-user
            [:div.row
             [:div#main-content
              (let [component (routes/dispatch route-data)]
                (om/build component app))]
             [:div#sidebar
              (om/build notifications-component (:current-user app))]])]]))))

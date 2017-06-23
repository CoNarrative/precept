(ns fullstack.ws
  (:require [taoensso.sente :as sente]
            [precept.core :refer [then]]
            [mount.core :refer [defstate]]))

(defstate socket
  :start (sente/make-channel-socket! "/chsk" {:type :auto}))

(defmulti handle-message first)

(defmethod handle-message :promotions/current
  [[msg-name msg]]
  (then msg))

(defmulti handle-event :id)

(defmethod handle-event :chsk/state [_])

(defmethod handle-event :chsk/handshake [_])

(defmethod handle-message :chsk/ws-ping [_])

(defmethod handle-event :chsk/recv [{:keys [?data]}]
  (handle-message ?data))

(defstate router
  :start (sente/start-chsk-router! (:ch-recv @socket) handle-event))

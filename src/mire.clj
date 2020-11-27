#!/usr/bin/env clj

(add-classpath (str "file://" (.getParent (java.io.File. *file*)) "/"))

(ns mire
  (:use [mire commands rooms player])
  (:use [clojure.contrib server-socket duck-streams]))

(def port 3333)
(def prompt "> ")
;; Функция синхронно удаляет игрока из списка игроков комнаты и помещает его инвентарь в инвентарь комнаты
(defn cleanup []
  "Drop all inventory and remove player from room and player list."
  (dosync
   (map discard @*inventory*)
   (commute (:inhabitants @*current-room*)
            disj *player-name*)))

(defn- mire-handle-client [in out]
  (binding [*in* (reader in)
            *out* (writer out)]

    ;; We have to nest this in another binding call instead of using
    ;; the one above so *in* and *out* will be bound to the socket
    (print "\nWhat is your name? ") (flush)
    (binding [*player-name* (read-line)
              *current-room* (ref (rooms :start))
              *inventory* (ref #{})]
      (dosync (commute (:inhabitants @*current-room*) conj *player-name*))

      (println (look)) (print prompt) (flush)
      
      ;; Если происходит отключение игрока, то вызывается cleanup
      (try (loop [input (read-line)]
             (when input
               (println (execute input))
               (print prompt) (flush)
               (recur (read-line))))
           (finally (cleanup))))))

(set-rooms "data/rooms")
(defonce server (create-server port mire-handle-client))

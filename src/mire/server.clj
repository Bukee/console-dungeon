(ns mire.server
  (:use [mire.player]
        [mire.commands :only [discard look execute]]
        [mire.rooms :only [add-rooms rooms]])
  (:use [clojure.contrib.io :only [reader writer]]
        [clojure.contrib.server-socket :only [create-server]]))

(defn- cleanup []
  "Drop all inventory and remove player from room and player list."
  (dosync
   (doseq [item @*inventory*]
     (discard item))
   (commute player-streams dissoc *player-name*)
   (commute (:inhabitants @*current-room*)
            disj *player-name*)))

(defn- get-unique-player-name [name]
  (if (@player-streams name)
    (do (print "That name is in use; try again: ")
        (flush)
        (recur (read-line)))
    name))

(defn- mire-handle-client [in out]
  (binding [*in* (reader in)
            *out* (writer out)]

    ;; We have to nest this in another binding call instead of using
    ;; the one above so *in* and *out* will be bound to the socket
    (print "\nWhat is your name? ") (flush)
    (binding [*player-name* nil
              *current-room* (ref (rooms :start))
              *inventory* (ref #{})]
      (dosync
       (set! *player-name* (get-unique-player-name (read-line)))
       (commute (:inhabitants @*current-room*) conj *player-name*)
        ; Добоаляем выходной поток в мару потоков 
        ; для отправки сообшения другим игрокам
       (commute player-streams assoc *player-name* *out*))

      (println (look)) (print prompt) (flush)

      (try (loop [input (read-line)]
             (when input
               (println (execute input))
               (print prompt) (flush)
               (recur (read-line))))
           (finally (cleanup))))))

(defn -main
  ([port dir]
     (add-rooms dir)
     (defonce server (create-server (Integer. port) mire-handle-client))
     (println "Launching Mire server on port" port))
  ([port] (-main port "resources/rooms"))
  ([] (-main 3333)))

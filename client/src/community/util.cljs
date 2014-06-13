(ns community.util)

(defn throw-if-err
  "Accepts a single value, throwing it if it is a JavaScript Error
  and returning it otherwise."
  [value]
  (if (instance? js/Error value)
    (throw value)
    value))

(defn map-vals
  "Map a function over the values of a map."
  [f m]
  (into {} (for [[k v] m] [k (f v)])))

(defn log [thing]
  (.log js/console thing))

(defn human-format-time [unix-time]
  (-> (* unix-time 1000)
      (js/Date.)
      (.toDateString)))

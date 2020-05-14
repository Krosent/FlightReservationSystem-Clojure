(ns input-test-def)


(defn uuid [] (.toString (java.util.UUID/randomUUID)))

(def flights_gen
  (let [flights (for [id (range 1200) :let [from (uuid) to (uuid)]]
    {:id id 
    :from from :to to
    :carrier (str "Carrier: " (rand-int 10))
    :pricing [
        [100 15000 0]
        [150  5000 0]
        [200  5000 0]
        [300  5000 0]]
    }
    )]
    ;@flights
    ;(println flights)
    flights
  )
)

(def customers
  (for [id (range 1200)
        :let [{from :from to :to} (nth flights_gen (mod id 1200))]]
    {:id     id
     :from   from
     :to     to
     :seats  2;(+ (rand-int 4) 1)        ; 1-4
     :budget 300}));(+ (rand-int 600) 200)})) ; 200-799


(def carriers (distinct (map :carrier flights_gen)))
;carriers
;(.getClass (flights_gen)))

(def TIME_BETWEEN_SALES 25) ; milliseconds
(def TIME_OF_SALES 25)

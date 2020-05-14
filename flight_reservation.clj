(ns flight-reservation
  (:require [clojure.string]
            [clojure.pprint]
            #_[input-simple :as input]
            [input-10kCustomers :as input] ; Input file with 10 customers inside
            #_[input-test-def :as input]
            #_[input-test-def888 :as input]
            #_[input-random :as input]))

(def logger (agent nil))
;(defn log [& msgs] (send logger (fn [_] (apply println msgs))))
(defn log [& msgs] nil)

(def flights
  "All flights are encapsulated in a single atom in this implementation.
  You are free to change this to a more appropriate mechanism."
  ; Replace atoms to agents since atoms have sync access while we need async access to data. 
  (atom []))

(defn initialize-flights [initial-flights]
  "Set `flights` atom to the `initial-flights`."
  ; Each flight is a ref
  (reset! flights (map (fn [flight] (ref flight)) initial-flights)))

(defn print-flights [flights]
  "Print `flights`."
  (letfn [(pricing->str [pricing]
            (->> pricing
              (map (fn [[p a t]] (clojure.pprint/cl-format nil "$~3d: ~3d ~3d" p a t)))
              (clojure.string/join ", ")))]
    (doseq [flight flights]
      (let [{:keys [id from to pricing]} @flight]
        (println (clojure.pprint/cl-format nil "Flight ~3d from ~a to ~a: ~a"
          id from to (pricing->str pricing))))
    )
  )
)

(defn- update-pricing [flight factor]
  ; Update pricing function with concurrency. We can use pmap here (not sure, but we can!)
  "Updated pricing of `flight` with `factor`."
  
  (ref-set flight 
		(update @flight :pricing 
			#(map (fn [[p a t]] [(* p factor) a t]) %))))

(defn start-sale [carrier]
  ; The same as for update-pricing
  "Sale: all flights of `carrier` -20%."
  (log "Start sale for" carrier "!")

  (fn [current-flights]
    (doseq [flight flights] 
      (dosync
        (if (= (:carrier flight) carrier)
        (ref-set flight (update-pricing flight 0.80))
        flight)
        )
    )
  )
)

(defn end-sale [carrier]
  "End sale: all flights of `carrier` +25% (inverse of -20%)."
  (log "End sale for" carrier "!")
  (fn [current-flights]
    (doseq [flight flights] 
      (dosync
        (if (= (:carrier flight) carrier)
        (ref-set flight (update-pricing flight 1.25))
        flight)
        )
    )
  )
)

(defn sort-pricing [pricing]
  "Sort `pricing` from lowest to highest price."
  (sort-by first pricing))

(defn filter-pricing-with-n-seats [pricing seats]
  "Get `pricing` for which there are at least `seats` empty seats available."
  (filter #(>= (second %) seats) pricing))

(defn lowest-available-price [flight seats]
  "Returns the lowest price in `flight` for which at least `seats` empty seats
  are available, or nil if none found."

  (-> (:pricing flight)            ; [[price available taken]]
    (filter-pricing-with-n-seats seats)
    (sort-pricing)
    (first)   ; [price available taken]
    (first)   ; first
  )
)

(defn- find-flight [customer]
  "Find a flight in `flights` that is on the route and within the budget of
  `customer`. If a flight was found, returns {:flight flight :price price},
  else returns nil."
  (let [{:keys [_id from to seats budget]}
          customer
        flights-and-prices
          ; flights that are on the route and within budget, and their price
          (for [f @flights
                :when (and (= (:from @f) from) (= (:to @f) to))
                :let [lowest-price (lowest-available-price @f seats)]
                :when (and (some? lowest-price) (<= lowest-price budget))
                ]
            {:flight f :price lowest-price})
        cheapest-flight-and-price
          (first (sort-by :price flights-and-prices))]
    cheapest-flight-and-price)
)

(defn- book [flight price seats]
  "Updates `flight` to book `seats` at `price`."
        (dosync
          (let [result (filter (fn [[p a t]] (and (= p price) (>= a seats))) (:pricing @flight))]
          (if (not (empty? result)) 
            (do
              ;(println result)
              (ref-set flight 
                (update @flight :pricing
                  (fn [pricing]
                    (for [[p a t] pricing]
                      (if (= p price)
                        [p (- a seats) (+ t seats)]
                        [p a t])
                    )
                  )
                ))
              true)
            false))
          )
)

(defn- process-customer [customer]
  "Try to book a flight from `flights` for `customer`, returning the updated
  flight if found, or nil if no suitable flight was found."
  (dosync
    (if-let [{:keys [flight price]} (find-flight customer)]
      (let [updated-flight (book flight price (:seats customer))] 
        (log "Customer" (:id customer) "booked" (:seats customer)
          "seats on flight" (:id @flight) "at $" price " (< budget of $"
          (:budget customer) ").")
        updated-flight)
      (do
        (log "Customer" (:id customer) "did not find a flight.")
        nil))
  )
)

(def finished-processing?
  "Set to true once all customers have been processed, so that sales process
  can end."
  (atom false))

(defn process-customers [customers]
  (doall (pmap (fn [customer] (process-customer customer)) customers))
  (reset! finished-processing? true))

(defn process-customers-internal-func [customers]
(doseq [customer customers] (process-customer customer)))

;(comment "
; Pile algorithm taken from StackOverflow -> https://stackoverflow.com/a/26484836/8709654
(defn piles [n coll]
  (let [heads (take n (iterate rest coll))]
    (doall (map (partial take-nth n) heads))
  )
)
;)

(defn process-customers-nthreads [customers nThreads]
  (let [customersList (piles nThreads customers)]
      (do 
        (doall (pmap (fn [this_customers] (process-customers-internal-func this_customers)) customersList))
        (reset! finished-processing? true)
      )
  )
)

 
(defn sales-process [carriers]
  "The sales process starts and ends sales periods, until `finished-processing?`
  is true."
  (loop []
    (let [discounted-carrier (rand-nth carriers)]
      (Thread/sleep input/TIME_BETWEEN_SALES)
      (start-sale discounted-carrier)
      (Thread/sleep input/TIME_OF_SALES)
      (end-sale discounted-carrier)
    )
    (if (not @finished-processing?)
      (recur))))

(defn main []
  (initialize-flights input/flights_gen)
  (let [f1 (future (time (process-customers-nthreads input/customers 8)))
        f2 (future (sales-process input/carriers))]
    @f1
    @f2)
  ;(println "Flights:")
  ;(print-flights @flights)
)

; Load Main Function 30 Times for benchmarking purposes
(dotimes [n 30] (main))
(main)

(shutdown-agents)
;(main)
;(await logger) ; if use logger, for testing


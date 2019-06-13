(ns orders
  (:require [mappings.core :as mappings]))

;; we establish global mappings here,
;; but it is possible to establish contextual or local mappings
(mappings/add
  "The gross is the price * quantity"
  (:line/gross [:line/price :line/quantity] *)

  "The total is the gross - discount, or 0.0 whichever is higher"
  (:line/total [:line/gross :line/discount] -)

  "Discount is 0.0 if not otherwise specified"
  (:line/discount [] (constantly 0.0))

  "The line discount will be the gross - total"
  (:line/discount [:line/gross :line/total] -))

;; here we ask for just the total using the outspec [:line/total]
(mappings/select {:line/quantity 3, :line/price 4.0} [:line/total])
;; =>
#:line{:total 12.0}

;; here we ask for the only the keys total and gross
(mappings/select {:line/quantity 3, :line/price 4.0} [:line/total :line/gross])
;; =>
#:line{:total 12.0, :gross 12.0}

;; here we ask for a the bare total, no vector is used in the output spec
(mappings/select {:line/quantity 3, :line/price 4.0} :line/total)
;; =>
12.0

;; here we ask for just the bare discount, we have no input - so the default
;; must be used
(mappings/select {} :line/discount)
;; =>
0.0

;; default rules (rules with no requirements) have lower precedence
(mappings/select {:line/total 10.0, :line/gross 11.0} :line/discount)
;; =>
1.0

;; we can also ask for the selected keys to just be added to the input using
;; a set as the output spec
(mappings/select {:line/quantity 3, :line/price 4.0} #{:line/total})
;; =>
#:line{:quantity 3, :price 4.0, :gross 12.0, :discount 0.0, :total 12.0}

;; you can use `selection` to precompile code for a particular output spec
(let [f (mappings/selection [:line/discount :line/gross])]
  (= #:line{:total 12.0, :gross 12.0} (f {:line/quantity 3, :line/price 4.0})))

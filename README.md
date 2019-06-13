# mappings

**Currently highly experimental with no tests!**

Mappings lets you declare relationships between keys in maps and leverage those relationships to:

- Automate key conversion and calculation
- Derive docstrings, assertions, and specs
- Automatic wiring of dependent calculations

```clojure

;; This adds rules to the ::math ruleset.
;; if you omit the keyword ::math the rules would be added to the default
;; ruleset, but we do not want to do that - as we are not using globally qualified
;; keys.

(mappings/add ::math
  "Relationships between keys are established with rules.

  This rule specifies that :a+1 == (inc :a) and that :a = (dec :a+1)."
  (:a+1 :a :fn inc :rfn dec)

  "The most basic rule is equivalence, here we say b == a, and therefore a == b."
  (:b :a)

  "We can have one way relationships, that require multiple keys, in this case you
  can omit the :fn keyword."
  (:d [:e :f] +)

  "We can use functions that take unordered dependencies these receive their
  args as map"
  (:d2 #{:e :f} (fn [m] (reduce + (vals m)))))

;; here we select the desired output :a+1 using the ::math ruleset.
(mappings/select {:a 1} ::math :a+1)
;; =>
2

;; here we ask for multiple outputs using a vector as the output spec.
;; we get a map.
(mappings/select {:a 1} ::math [:a+1 :b])
;; =>
{:a+1 2, :b 1}

;; Transitive properties are observed, here b == a therefore :a+1 can be computed.
(mappings/select {:b 1} ::math :a+1)
;; =>
2

;; If I want to add keys to my input, you can use a set rather than a vector
;; in the output spec position
(mappings/select {:e 1 :f 2} ::math #{:d})
;; =>
{:e 1, :f 2, :d 3}

```

## Usage

FIXME


## License

Copyright © 2019 Riverford Organic Farmers Ltd

Distributed under the [3-clause license ("New BSD License" or "Modified BSD License").](http://github.com/riverford/mappings/blob/master/LICENSE)
# mappings

**Currently highly experimental with no tests!**

Allows you to provide definitions of the calculation used to provide keys in your program.
If spec lets you specify values a key can have, then mappings lets you specify how you arrive at those values.

e.g key :c is (+ :a :b).

What can you do with mappings?

- Wires up functions of map -> map for you based on rulesets
- Derive docstrings, assertions, and specs
- Code generation able to follow symmetries and transitive relationships for you so you don't have to

```clojure

;; This adds rules to the ::math ruleset.
;; if you omit the keyword ::math the rules would be added to the default
;; ruleset, but we do not want to do that - as we are not using globally qualified
;; keys.

(mappings/add ::math
  "Relationships between keys are established with rules.

  This rule specifies that :a+1 == (inc :a) and that :a = (dec :a+1)."
  (:a+1 :a :fn inc :rfn dec)

  "The most basic rule is to define symmetry, here we say b == a, and therefore a == b."
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

;; If we want to add keys to the input, we can use a set rather than a vector
;; in the output spec position
(mappings/select {:e 1 :f 2} ::math #{:d})
;; =>
{:e 1, :f 2, :d 3}

```

## Todo 

Mappings is still in the experimental stage, I do not recommend using this in production. 

Much more work needs to be done before this is ready!

- Compile time optimisations (e.g fully inlined selection code)
- Documentation 
- Tests
- Spec integration
- Uninstallability via code generation of equivalent functions

## License

Copyright © 2019 Riverford Organic Farmers Ltd

Distributed under the [3-clause license ("New BSD License" or "Modified BSD License").](http://github.com/riverford/mappings/blob/master/LICENSE)
# mappings

**Currently highly experimental with no tests!**

Mappings lets you specify relationships between keys in maps via `rules`.

An example rule in plain english:

`:c is (+ :a :b).`

Or 

`:foo_bar is :my.ns/foo-bar`

### Ok so why?

Using many of these relationships between keys, you can have mappings figure out if possible how to get 
to some set of output keys given a map, using the dependency graph of the rules to drive computation.

### What else can it do?

- Automate repetitive key wiring and contains? style checks in functions taking maps.
- Derive docstrings, assertions, and certain specs
- Code generation able to follow symmetries and transitive relationships for you so you don't have to

```clojure

;; This adds rules to the ::math ruleset.
;; if you omit the keyword ::math the rules would be added to the default
;; ruleset, but we do not want to do that - as we are not using globally qualified
;; keys.

(mappings/add ::math
  "Relationships between keys are established with rules.
   
   The most basic rule is to define symmetry, here we say b == a, and therefore a == b."
  (:b :a)
 
  "This rule uses functions to specify that :a+1 == (inc :a) and that :a = (dec :a+1)."
  (:a+1 :a :fn inc :rfn dec)

  "Any function can be used"
  (:c :b :fn (fn [b] (+ b 2)) :rfn (partial - 2))

  "One way relationships are possible, that perhaps require multiple keys"
  (:d [:e :f] :fn +)
  
  "With one way relationships, you can omit the :fn if you want"
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
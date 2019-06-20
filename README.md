# mappings

**Currently highly experimental, we do not use it in production, its just an idea! (with no tests!)**

`mappings` lets you specify relationships between keys in maps via `rules`,
with these rules, functions of maps returning maps can derived for you.

Here are two rules in pseudo code 

- `:c is :a + :b` Establishes that `:c` for a map `m` can be derived by the calculation `(+ (:a m) (:b m))`.
- `:foo_bar is :my.ns/foo-bar` Establishes that `:foo_bar` and `:my.ns/foo-bar` are interchangeable

### What can `mappings` do?

- Define relationships of equivalence or transformation
  ```clojure
   (mappings/add
    (:c [:a :b] +)
    (:foo_bar :my.ns/foo_bar))
  ```

- Use `selection` to compile functions that derive the desired output keys from the input using rules.
  ```clojure
    (def f (mappings/selection [:c]))
    (f {:a 1, :b 2}) ;; => {:c 3} 
  ```
  
- Use `select` for immediate selection, less efficient - but useful as a fancier `rename-keys`
  ```clojure 
   (mappings/select {:foo_bar "abc"} [:my.ns/foo-bar])
   ;; =>
   {:my.ns/foo-bar "abc"}  
  ```
  
- Ask how keys can be calculated using `providing` 
  ```clojure 
   (mappings/providing :c)
   ;; =>
   ({:rid 0,
     :ns user
     :doc "Anonymous mapping"
     :req #{:a :b}
     :fn +}) 
  ```
  
- Define reversable rules
  ```clojure
  (mappings/add 
    (:d :a :fn inc :rfn dec))
 
  (mappings/select {:a 1} [:d]) ;; => {:d 2}
  (mappings/select {:d 2} [:a]) ;; => {:a 1}  
  ```
  
- Document your rules
  ```clojure
   (mappings/add 
    "c is a + b"
    (:c [:a :b] +))
  
   ;; is the same as
   (mappings/add 
    (:c [:a :b] :fn + :doc "c is a + b"))  
  ``` 
  
- Rules carry transitively, including via reverse relationships
  ```clojure 
  (mappings/add 
    (:e [:c :d] +))
 
  (mappings/select {:a 1, :b 2} [:e]) 
  ;; will perform the below
  ;; let c = a + b 
  ;; let d = a + 1
  ;; return c + d
  ;; => 
  {:e 5} 
  ```
- Calculations are performed only if a key has not been provided
  
   ```clojure 
     (mappings/add 
       (:e [:c :d] +))
    
     (mappings/select {:a 1, :b 2, :d 42} [:e]) 
     ;; will perform the below
     ;; let c = a + b
     ;; because d is provided as 42 
     ;; let d = 42 
     ;; return c + d
     ;; => 
     {:e 45} 
   ``` 

- There are no maybe sheep
  ```clojure
  (mappings/select {:a 1} [:c]) 
  ;; No :b provided =>
  {}
  ```

## Rule shape

A rule comprises an output-spec, and input-spec
and optional transforms.

Like so:

`(output-spec input-spec :fn transform :rfn reverse-transform)`

If the relationship can only be traverse input -> output, you can elide the :fn keyword

`(output-spec input-spec transform)`

If the relationship should just be equivalence, then just omit the transforms

`(output-spec input-spec)`


### Rule output spec

- A `keyword` so the `:fn`: simply provides the value
- A `vector` in which case the `:fn` should provide a map containing keys in the vector.

### Input spec

- A `keyword` in which the `:fn` will receive the value as the first (and only) argument.
- A `vector` in which case the `:fn` will receive the key values as arguments in the order they are defined in the vector, you can specify an empty vector for 
  keys that can be provided with no inputs.
- A `set` in which case the `:fn` will receive a map with at least the keys in the set.
- A map with `:req` and `:opt` keys. In which case the `:fn` will receive a map with at least the keys in `:req` and if possible the keys in `:opt`.


### Selection output spec

When using the `select` or `selection` functions you 
provide an output spec which informs what you will receive as the result of 
computing the selection.

- A `keyword` will return you the value for that keyword
- A `vector` will provide you a map containing at the keys in the vector that could be computed.
- A `set` will provide you the input map + any intermediate keys that could be derived + the keys in the set that could
  be computed.
- A map containing `:req` and `:opt`, will return you a map with all of the keys in `:req` (or an exception is thrown)
  in addition will return you keys in `:opt` if they can be derived.
 
e.g 

```clojure
(mappings/select {:a 1, :b 2} :c) ;; => 3 
(mappings/select {:a 1, :b 2} [:c :a]) ;; => {:c 3, :a 1}
(mappings/select {} {:req [:c]}) ;; => throws AssertionError with a nice message
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

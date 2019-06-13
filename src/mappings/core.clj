(ns mappings.core
  (:require [mappings.impl.mapping :as mapping]
            [mappings.impl.global :as global]
            [mappings.impl.compile :as compile]
            [mappings.impl.ruleset :as ruleset]))

(defn selection
  ([outspec] (selection (global/get-rules :default) outspec))
  ([ruleset outspec]
   (let [f (compile/selection
             (if (keyword? ruleset)
               (global/get-rules ruleset)
               ruleset)
             (if (coll? outspec)
               outspec
               [outspec])
             #{})]
     (cond
       (vector? outspec) (fn [m] (select-keys (f (transient m)) outspec))
       (set? outspec) (fn [m] (persistent! (f (transient m))))
       :else (fn [m] (get (f (transient m)) outspec))))))

(defmacro select
  ([m outspec] `(select ~m :default ~outspec))
  ([m ruleset outspec]
   `((selection ~ruleset ~outspec) ~m)))

(defmacro add
  [ruleset? & forms]
  (if (keyword? ruleset?)
    `(binding [mapping/*ruleset-name* ~ruleset?]
       (global/add-rule ~ruleset? (mapping/mappings ~@forms)))
    `(mappings.core/add :default ~@forms)))

(comment
  (let [rules
        (ruleset/ruleset
          (mappings (:b :a :fn inc :rfn dec)
                    (:c [:a :b] +)))

        f (selection
            rules
            [:a :c])]
    (f {:b 42})))
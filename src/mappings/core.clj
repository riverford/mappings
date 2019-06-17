(ns mappings.core
  (:require [mappings.impl.mapping :as mapping]
            [mappings.impl.global :as global]
            [mappings.impl.compile :as compile]
            [mappings.impl.ruleset :as ruleset]
            [mappings.impl.rule :as rule]))

(defn selection
  "Compiles a selection that provides an output matching `outspec`.

  Examples:

  select-keys style selection from named ruleset

    (selection ::math [:c])

  Single value selection from global ruleset

    (selection :c)"
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
  "Adds the rules to the named ruleset, if the ruleset is not specified, the rules
  will be added to the global ruleset.

  Examples:

  Add to global ruleset:

   (add (:a :b) (:c [:a :b] +))

  Add to named ruleset:

   (add ::math (:a :b) (:c [:a :b] +))"
  [ruleset? & rules]
  (if (keyword? ruleset?)
    `(binding [mapping/*ruleset-name* ~ruleset?]
       (global/add-rule ~ruleset? (mapping/mappings ~@rules)))
    `(mappings.core/add :default ~@rules)))

(defmacro defruleset
  "Defines named ruleset, if a ruleset already exists, it is replaced entirely.

  See add for rule syntax."
  [ruleset & rules]
  `(binding [mapping/*ruleset-name* ~ruleset]
     (global/replace-rules ~ruleset (mapping/mappings ~@rules))))

(defn paths
  [ruleset outspec]
  (let [ruleset (if (keyword? ruleset)
                  (global/get-rules ruleset)
                  ruleset)]
    ((fn ! [outspec seen]
       (for [k outspec
             :let [ruleids (-> ruleset ::ruleset/provision (get k))]]
         {:key k
          :via
          (vec
            (for [ruleid ruleids
                  :let [rule (get (::ruleset/rules ruleset) ruleid)]]
              (rule/ruleform rule)))}))
      outspec #{})))

(comment
  (let [rules
        (ruleset/ruleset
          (mappings (:b :a :fn inc :rfn dec)
                    (:c [:a :b] +)))

        f (selection
            rules
            [:a :c])]
    (f {:b 42})))
(ns mappings.impl.global
  (:require [mappings.impl.ruleset :as ruleset])
  (:import (java.util.concurrent ConcurrentHashMap)
           (java.util Map)))

(defonce ^:private ^Map rulesets
  (ConcurrentHashMap.))

(defn get-rules
  [k]
  (.get rulesets k))

(defn update-ruleset
  [k f & args]
  (let [rules (.get rulesets k)]
    (if rules
      (.put rulesets k (apply f rules args))
      (.put rulesets k (apply f (ruleset/ruleset) args)))
    nil))

(defn add-rule
  [k rule]
  (update-ruleset k ruleset/add rule)
  nil)

(defn delete-rule
  [k rid]
  (update-ruleset
    k
    (fn [ruleset]
      (if-some [id (ruleset/identify ruleset rid)]
        (ruleset/remove-rule ruleset id)
        ruleset))))

(ns mappings.impl.global
  (:require [mappings.impl.ruleset :as ruleset])
  (:import (java.util.concurrent ConcurrentHashMap)
           (java.util Map)))

(defonce ^:private ^Map rulesets
  (ConcurrentHashMap.))

(defn get-rules
  [k]
  (.get rulesets k))

(defn add-rule
  [k rule]
  (let [rules (.get rulesets k)]
    (if rules
      (.put rulesets k (ruleset/add rules rule))
      (.put rulesets k (ruleset/add (ruleset/ruleset) rule))))
  nil)

(defn remove-rule
  [k id]
  (let [rules (.get rulesets k)]
    (if rules
      (.put rulesets k (ruleset/remove-rule rules id))))
  nil)

(defn replace-rules
  [k & rules]
  (.put rulesets k (apply ruleset/ruleset rules))
  nil)
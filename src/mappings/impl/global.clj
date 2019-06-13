(ns mappings.impl.global
  (:require [mappings.impl.ruleset :as ruleset]))

(defonce ^:private global-rules
  (atom (ruleset/ruleset)))

(defn get-ruleset
  []
  @global-rules)

(defn add-rule
  [rule]
  (swap! global-rules ruleset/add rule)
  nil)

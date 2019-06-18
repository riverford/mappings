(ns mappings.impl.compile
  (:require [clojure.set :as set]
            [mappings.impl.rule :as rule]
            [mappings.impl.ruleset :as ruleset]))

(set! *warn-on-reflection* true)

(defn selection
  [ruleset keyseq seen]
  (if (empty? keyseq)
    identity
    (let [{:keys [::ruleset/provision
                  ::ruleset/rules]
           :or {rules {}
                provision {}}} ruleset

          keyset (set keyseq)

          all-deps (set (for [needed-key keyset
                              rule-id (get provision needed-key)
                              :let [rule (get rules rule-id)]
                              required-key (::rule/required-keys rule)]
                          required-key))

          rules (into {} (for [needed-key keyset
                               :let [rule-ids (get provision needed-key)
                                     rules (->> rule-ids
                                                (map rules)
                                                (sort-by (comp count ::rule/required-keys))
                                                (reverse)
                                                vec)]
                               :when (seq rules)]
                           [needed-key rules]))

          all-deps (set/difference all-deps seen)
          seen (set/union seen all-deps)
          dep-selection (selection ruleset all-deps seen)

          run-key (fn [m needed-key]
                    (if-some [ev (m needed-key)]
                      (assoc! m needed-key ev)
                      (let [valid-rules (rules needed-key)]
                        (if (some? valid-rules)
                          (if (= 1 (count valid-rules))
                            (let [rule (nth valid-rules 0)]
                              (rule/execute! m rule))
                            (reduce
                              rule/execute!
                              m
                              valid-rules))
                          m))))]
      (if (empty? keyset)
        identity
        (fn run-selection [m]
          (reduce run-key (dep-selection m) keyset))))))
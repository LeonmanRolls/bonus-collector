(ns nngbc.example-test
    (:require [clojure.spec.test :as ts :refer [check]]))

(println
    (ts/summarize-results
        (ts/check (ts/checkable-syms) {::clojure.spec.test.check/opts {:num-tests 1}})))



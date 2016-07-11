(ns nngbc.core-test
  (:require-macros [cljs.test :refer (is deftest testing)])
  (:require
    [nngbc.core :refer (header)]
    [cljs.test]
    [cljs.spec.test :as ts]))

#_(deftest example-passing-test
  (is (= 1 1)))
(println "hi there")
(println (ts/run-all-tests))


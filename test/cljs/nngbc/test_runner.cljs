(ns nngbc.test-runner
  (:require
   [doo.runner :refer-macros [doo-tests]]
   [nngbc.core-test]
   [nngbc.common-test]))

(enable-console-print!)

(doo-tests 'nngbc.core-test
           'nngbc.common-test)



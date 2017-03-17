(ns todomvc.runner)
(:require [doo.runner :refer-macros [doo-tests]]
          [todomvc.rules-test])

(doo-tests 'todomvc.rules-test)

(ns todomvc.rules-test
  (:require-macros [clara.macros :refer [defsession]])
  (:require [cljs.test :refer-macros [deftest testing is testing run-tests]]
            [todomvc.rules :refer [todos
                                   todo-tx
                                   visibility-filter-tx
                                   toggle-tx
                                   map->tuple
                                   find-done-count]]
            [clara.rules :refer [query insert insert-all fire-rules]]))

(defn mk-todo [done]
  (todo-tx (random-uuid) "Title!" done))

(defn mk-session-with [session facts]
  (-> session
    (insert-all facts)
    (fire-rules)))

(deftest rules
  (testing "find-done-count"
    (testing "should return 0 if no todos done"
      (let [facts   (apply concat (map map->tuple (repeatedly 5 #(mk-todo nil))))
            session (mk-session-with todos facts)]
        (println "facts" facts)
        (is (= 0 (:?count (first (query session find-done-count)))))))
    (testing "should return 5 if 5 todos done"
      (let [facts  (apply concat (map map->tuple (repeatedly 5 #(mk-todo :done))))
            session (mk-session-with todos facts)]
        (is (= 5 (:?count (first (query session find-done-count)))))))))

(run-tests)
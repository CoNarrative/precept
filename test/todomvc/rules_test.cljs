(ns todomvc.rules-test
  (:require-macros [clara.macros :refer [defsession]])
  (:require [cljs.test :refer-macros [deftest testing is testing run-tests]]
            [todomvc.rules :refer [todos
                                   todo-tx
                                   visibility-filter-tx
                                   toggle-tx
                                   map->tuple
                                   entities-where
                                   find-done-count]]
            [clara.rules :refer [query insert insert-all fire-rules]]))

(defn mk-todo [done]
  (todo-tx (random-uuid) "Title!" done))

(defn insert-tuples [session tups]
  (insert-all session (apply concat tups)))

(defn insert-fire! [session facts]
  (-> session
    (insert-tuples facts)
    (fire-rules)))

(deftest rules
  (testing "find-done-count"
    (testing "should return 0 if no todos done"
      (let [facts   (map map->tuple (repeatedly 5 #(mk-todo nil)))
            session (insert-fire! todos facts)]
        (println "facts" facts)
        (is (= 0 (:?count (first (query session find-done-count)))))))
    (testing "should return 5 if 5 todos done"
      (let [facts   (map map->tuple (repeatedly 5 #(mk-todo :done)))
            session (insert-fire! todos facts)]
        (is (= 5 (:?count (first (query session find-done-count))))))))

  (testing "show-all"
    (testing "datoms with :todo/visible should equal num of todos
              Note: :todo/visible is false by default at insertion time"
      (let [facts   (map map->tuple (repeatedly 5 #(mk-todo :done)))
            session (insert-fire! todos facts)]
        (is (= 0 (count (entities-where session :todo/visible true))))))))
(run-tests)
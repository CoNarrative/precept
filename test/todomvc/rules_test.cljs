(ns todomvc.rules-test
  (:require-macros [clara.macros :refer [defsession]])
  (:require [cljs.test :refer-macros [deftest testing is testing run-tests]]
            [todomvc.facts :as facts]
            [todomvc.rules :refer [todos
                                   map->tuple
                                   find-all-done
                                   find-done-count]]
            [todomvc.util :refer [map->tuples
                                  entities-where
                                  insert-fire!]]
            [clara.rules :refer [query insert insert-all fire-rules]]))

(defn mk-todo [done]
  (facts/todo (random-uuid) "Title!" done))

(def num-todos 5)

(deftest rules
  (testing "find-done-count"
    (testing "should return 0 if no todos done"
      (let [facts   (map map->tuples (repeatedly num-todos #(mk-todo nil)))
            session (insert-fire! todos facts)]
        (is (= 0 (:?count (first (query session find-done-count)))))))
    (testing "should return 5 if 5 todos done"
      (let [facts   (map map->tuples (repeatedly num-todos #(mk-todo :done)))
            session (insert-fire! todos facts)]
        (is (= num-todos (:?count (first (query session find-done-count))))))))

  (testing "show-all"
    (testing "All visible when :ui/visibility-filter :all"
      (let [facts   (into
                      (vector (map->tuples (facts/visibility-filter (random-uuid) :all)))
                      (map map->tuples (repeatedly num-todos #(mk-todo :done))))
            session (insert-fire! todos facts)
            visible (entities-where session :todo/visible)]
        (is (= num-todos (count visible))))))

  (testing "show-done"
    (testing "Todos with status :done only when :ui/visibility-filter :done"
      (let [facts   (concat
                      (vector (map->tuples (facts/visibility-filter (random-uuid) :done)))
                      (vector (map->tuples (mk-todo nil)))
                      (mapv map->tuples (repeatedly (dec num-todos) #(mk-todo :done))))
            session (insert-fire! todos facts)
            done    (query session find-all-done)
            visible (entities-where session :todo/visible)]
        (is (= (dec num-todos) (count visible)))))))

(run-tests)

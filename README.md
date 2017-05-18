# Precept
A functional relational programming framework

[![CircleCI](https://circleci.com/gh/CoNarrative/precept.svg?style=shield&circle-token=b23498670888edf670832326d50f9d8fab60b2e3)](https://circleci.com/gh/CoNarrative/todomvc)

### Truly declarative
> "You need only specify what you require, not how it must be achieved." - Out
of the Tar Pit

The first thing the authors of React say about their library is that is it declarative. Regardless of whether that's an accurate statement, we find it significant. Declarative is desirable. And we agree.

```clj
(rule inc-count-when-tick
  [[_ :tick]]
  [[_ :count ?v]]
  =>
  (insert! [:global :count (inc ?v)])

(defsub :count  
  [[_ :count ?v]]
  =>
  {:count ?v})

(def-tuple-session my-session 'my-proj.my-ns)

(defn counter []
  (let [count @(subscribe :count)]
        _ (.setTimeout js/window #(then [:transient :tick true]))
    [:div count])

(reagent.render [counter])
```

### Pattern matching

### Rules engine
Precept would not be possible without [Clara](http://www.github.com/cerner/clara-rules), a ground-up implementation of the Rete algorithm in Clojure and Clojurescript. Our approach is
predicated upon a rules engine that runs in the browser. We believe Clara is the
first to have accomplished this in earnest, and that a truly declarative
approach to front-end web development would have already become popular if
the underlying technology they've developed was readily available.

# Precept
A functional relational programming framework

[![CircleCI](https://circleci.com/gh/CoNarrative/precept.svg?style=shield&circle-token=b23498670888edf670832326d50f9d8fab60b2e3)](https://circleci.com/gh/CoNarrative/todomvc)

| [Docs](https://conarrative.github.io/precept/) |

### Truly declarative

> "You need only specify what you require, not how it must
be achieved." - Out of the Tar Pit

The first thing the authors of React say about their library is that is it
declarative. Regardless of whether that's an accurate statement, we find it
significant. Declarative is desirable. And we agree.

```clj
(rule inc-count-when-tick
  [[_ :tick]]
  [[_ :count ?v]]
  => (insert! [:global :count (inc ?v)]))

(session my-session 'counter-ns)

(defn counter []
  (let [count @(watch [:count])]
    [:div count]))

(.setTimeout js/window #(then [:transient :tick true]) 1000)

(reagent/render [counter] (.-body js/document))

(start! {:session my-session :facts [[:global :count 0]]}) ```
```

### Rules engine
> “In the ideal world, **we** are not concerned with
performance, and our language and infrastructure provide all the general
support we desire.” - Out of the Tar Pit, emphasis added

Precept would not be possible without
[Clara](http://www.github.com/cerner/clara-rules),  a ground-up implementation
of the Rete algorithm in Clojure and Clojurescript. Our approach is predicated
upon a rules engine that runs in the browser. We believe Clara is the first to
have accomplished this in earnest, and that a truly declarative approach to
front-end web development would have already become popular if the underlying
technology they've developed was previously available.

The Rete algorithm creates and maintains optimized indexes for any data set that
allow efficient processing of incremental changes.

### Global state

State in precept is more or less a "bag of facts". In this way test test
it is no different from rules engines like Drools, Clara, and Jess. There is
no tree structure to reason over or organize. The session just contains a
bunch of tuples that can be grabbed at will. The fact of a key-code is on
the same level as a username.

### Persistence

We designed Precept with persistence in mind. One place rule
engines have historically made themselves irrelevant is their inability to
effectively write out to a database. This was something we wanted to solve
from the start.

We share the same data structure and even enforce the cardinality and uniqueness
of facts according to a Datomic schema. Our design makes separation of
persistent data from client-side only data trivial, and serialization to Datomic
in particular even more trivial, especially for those who supply a Datomic
schema to def-tuple-session. API helpers for this are forthcoming.

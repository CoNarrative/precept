# Precept
A functional relational programming framework

[![CircleCI](https://circleci.com/gh/CoNarrative/precept.svg?style=shield&circle-token=b23498670888edf670832326d50f9d8fab60b2e3)](https://circleci.com/gh/CoNarrative/todomvc)

| [Docs](https://conarrative.github.io/precept/) |

### Truly declarative

> "You need only specify what you require, not how it must
be achieved." - Out of the Tar Pit

The first thing the authors of React say about their library is that it's
declarative. Regardless of whether that's an accurate statement, we find it
significant. Declarative is desirable. We agree.

```clj
(rule inc-count-when-tick
  [[_ :tick]]
  [[?e :count ?v]]
  =>
  (insert! [?e :count (inc ?v)]))

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

The Rete algorithm creates and maintains efficient indexes for any data set. This
allows fast processing of incremental changes.

### Global state

State in Precept is more or less a "bag of facts". There is no tree structure to reason over or 
organize. The session just contains a bunch of tuples that can be grabbed at will. The fact of a 
key-code is on the same level as a username.

### Synchronized Reactive View Model

Because most view libraries and programming languages tend to operate more naturally with 
associative data structures than eav tuples, Precept converts and syncs all facts from the rules 
session to a view model. Components that subscribe to it are automatically rerendered when the data 
they're subscribed to changes.

### Persistence

We designed Precept with persistence in mind. One place rule
engines have historically made themselves irrelevant is their inability to
effectively write out to a database. This was something we wanted to solve
from the start.

Because Precept shares the same data structure and schema format, reading and writing with  
 Datomic is trivial. That said, we're committed to being database agnostic. 

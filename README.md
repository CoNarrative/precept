# Precept
A declarative programming framework

[![CircleCI](https://circleci.com/gh/CoNarrative/precept.svg?style=shield&circle-token=b23498670888edf670832326d50f9d8fab60b2e3)](https://circleci.com/gh/CoNarrative/todomvc)

[Docs](https://conarrative.github.io/precept/)
| [Example](https://github.com/CoNarrative/precept/tree/master/src/cljs/precept/todomvc)
| [Issues](https://github.com/CoNarrative/precept/issues)
| [Roadmap](https://github.com/CoNarrative/precept/projects/1)
| [Contributing](https://github.com/CoNarrative/precept/blob/master/CONTRIBUTING.md)


> "You need only specify what you require, not how it must
be achieved." - [Out of the Tar Pit](http://shaffner.us/cs/papers/tarpit.pdf)

> "If you want everything to be familiar, you will never learn anything new, because it can't be significantly different from what you already know." - Rich Hickey, Simple Made Easy


```clj
(rule inc-count-when-tick
  [[_ :tick]]
  [[?e :count ?v]]
  =>
  (insert! [?e :count (inc ?v)]))

(defsub :count)
  [[_ :count ?v]]
  =>
  {:count ?v}

(session my-session 'counter-ns)

(defn counter []
  (let [count @(subscribe [:count])]
    [:div count]))

(.setTimeout js/window #(then [:transient :tick true]) 1000)

(reagent/render [counter] (.-body js/document))

(start! {:session my-session :facts [[:global :count 0]]})
```

### Rules engine
> “In the ideal world, **we** are not concerned with
performance, and our language and infrastructure provide all the general
support we desire.” - Out of the Tar Pit, emphasis added

Precept wraps
[Clara](http://www.github.com/cerner/clara-rules), a ground-up implementation
of the Rete algorithm in Clojure and Clojurescript.  

We believe Clara is the first to have implemented a rules engine that runs in
the browser. Had this happened earlier, a truly declarative approach to
front-end web development might already have become popular.


### Global state

State in Precept is more or less a "bag of facts". There is no tree structure
to reason over or  organize. The session just contains a bunch of tuples that
can be grabbed at will. The fact of a  key-code is on the same level as a
username.

### Synchronized Reactive View Model

Because most view libraries and programming languages tend to operate more
naturally with  associative data structures than eav tuples, Precept converts
and syncs all facts from the rules  session to a view model. Components that
subscribe to it are automatically rerendered when the data they subscribe
to changes.

### Schema support
Precept enforces cardinality and uniqueness of facts according to user-supplied, Datomic-format schemas. This allows the declaration of one-to-many relationships in eav format:
```clj
(insert! [[?e :list/item "foo"]
          [?e :list/item "bar"]
          [?e :list/item "baz"])
```
Via subscriptions in the view layer, components receive `:list/item` as a list:
```clj
{:db/id 123 :list/item ["foo" "bar" "baz"]}
```
Unique attributes are handled using the same semantics as Datomic. Conflicts generate "error facts", which are inserted into the session so they may be resolved by rules.

You can have both a `:db-schema` and a `:client-schema`. This distinction between persistent and non-persistent facts is useful when writing to a database. Precept can hand you the facts you want to persist, while still allowing cardinality and uniqueness designations for client-side data.

### Future plans
The project board contains the most up-to-date information about what features are being discussed, prioritized, and worked on. Here's a few we're excited about:
- **Ruleset API** - Rules should be easily pluggable. We should be able to use general purpose rulesets the same way we use libraries. E.g. drag and drop, typeahead, etc.
- **General purpose algorithms** - Building complex, game-like UIs with animation sometimes requires algorithms for path-finding, tweening, collision detection, distance, etc. We want to write these applications, but we don't want to deal with the complexity of having half an application written in imperative code and the other half declarative. We're working to extend our DSL to support simple expressions like `(distance ?a ?b)` by calling performant, general-purpose algorithms for you.
- **Rendering views** - Working with a rules means we know what changes from one state to the next. Given that, we don't need React and its diff algorithm. It also means we don't need subscriptions. If we declare a view in the right hand side of a rule, we can render it when the facts it cares about change.

### Thanks
- [Clara](http://www.clara-rules.org/)

- [Datomic](http://www.datomic.com/)

- [re-frame](https://github.com/Day8/re-frame)

- [reagent](https://reagent-project.github.io/)

- [Mike Fikes](http://blog.fikesfarm.com/)

- [Dmitri Sotnikov](https://yogthos.net/index.html)
  and the [Luminus](http://www.luminusweb.net/) framework

- [Bruce Hauman](http://rigsomelight.com/) and
  [Figwheel](https://github.com/bhauman/lein-figwheel)

- [Cursive](https://cursive-ide.com/)

- [Precursor](https://github.com/PrecursorApp/precursor)

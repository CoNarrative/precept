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

## How it works
Precept manages state with rules. A `session` is a collection of rules and the facts that represent the state. Rules operate over facts as simple when/then statements. If the "when", or left-hand side (LHS), part of the rule is satisfied, the consequence, or right-hand side (RHS), is executed.

Rules can insert additional facts into the session to be processed by other rules. Other times, you may choose to execute an API call or other side effect. Ultimately, the consequence of a rule can be any valid Clojure or Clojurescript code.

Facts are expressed using primitive Clojure data structures and follow the same eav tuple convention as Datomic.
```clj
[123 :user/username "foo"]
```

Components subscribe to queries. They are rerendered whenever the data matching the query changes. Further, because subscription queries are rule treated as rules, they enjoy the same performance benefits as rules: They are not rerun if there is no possible way their results could have changed. See [Rete algorithm](#rules-engine).

Views insert new facts into the session with `then`. An `:on-click` handler that inserts a fact about the world looks like this:
```clj
[:button {:on-click #(then [:transient :fact-about-the-world (-> % .-target .-value)])}
 "Click me!"]
```

You may want to define your facts as functions or with `clojure.spec`. This can enhance readability, establish default values, and enforce value types.
```clj
;; facts.cljs

(defn button-click [e])
  [:transient :button/click (-> e .-target .-value)]


;; views.cljs

[:button {:on-click #(then (button-click %)])}]
  "Click me!"

```

Precept removes all facts with the entity id `:transient` at the end of every rule firing. This means `:button/click` can be treated as a transient event that does not survive across multiple rule firings.

Rules in the `:action` group can respond to facts being inser

```clj
(rule inc-count-when-tick
  {:group :action}
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
  (let [{:keys [count]} @(subscribe [:count])]
    [:div
      count]))
      [:button {:on-click #(then [:transient :])}]
(.setTimeout js/window #(then [:transient :tick true]) 1000)

(reagent/render [counter] (.-body js/document))

(start! {:session my-session :facts [[:global :count 0]]})
```

## Rete algorithm
> “In the ideal world, **we** are not concerned with
performance, and our language and infrastructure provide all the general
support we desire.” - Out of the Tar Pit, emphasis added

Precept wraps
[Clara](http://www.github.com/cerner/clara-rules), a ground-up implementation
of the Rete algorithm in Clojure and Clojurescript.  

We believe Clara is the first to have implemented a rules engine that runs in
the browser. Had this happened earlier, a truly declarative approach to
front-end web development might already have become popular.


## Global state

State in Precept is more or less a "bag of facts". There is no tree structure
to reason over or  organize. The session just contains a bunch of tuples that
can be grabbed at will. The fact of a  key-code is on the same level as a
username.

## Reactive View Model

All facts from the rules session are synced to a reactive view model. Components are rerendered only when the data they subscribe to changes.

Subscription results are returned as maps. Tuples are great for pattern matching, but maps are a better fit for components to generate markup from and for programming languages to iterate over. As a result, working with data in the view layer is the same as it is in most any frontend library or framework.

## Schema support
Precept enforces cardinality and uniqueness according to user-supplied, Datomic-format schemas. This allows the modeling of one-to-many relationships with eav tuples:

```clj
;; schema.cljs
(attribute :list/item
  :db/cardinality :db.cardinality/one-to-many)

;; rules.cljs
(insert! [[123 :list/item "foo"]
          [123 :list/item "bar"]
          [123 :list/item "baz"])
```

Components receive `:list/item` as a list:

```clj
{:db/id 123 :list/item ["foo" "bar" "baz"]}
```
Unique attributes are handled using the same semantics as Datomic for [:db.unique/identity](http://docs.datomic.com/identity.html#sec-4) and [:db.unique/value](http://docs.datomic.com/identity.html#sec-5). When there is a conflict, instead of throwing an error, Precept inserts facts about the error into the session so they it may be handled or resolved through rules.

Precept supports both a `:db-schema` and a `:client-schema` to allow users to distinguish persistent facts from non-persistent facts. They're both enforced the same way.

Precept can hand you the facts you want to persist, while still allowing you to define schema attributes your client-side only data.

## Where we're headed
The [project board](https://github.com/CoNarrative/precept/projects/1) contains the most up-to-date information about what features are being discussed, prioritized, and worked on. Here's a few we're excited about.

#### Rendering views

Because we use a rules engine, we know what changes from one state to the next. This means we don't need React and its diff algorithm to figure out "What changed?" for us. If we declare a view in the right hand side of a rule, we can render it when the facts it cares about change. We don't even need the concept of subscriptions.

#### Ruleset API

Rules are pluggable. We should be able to use general purpose rulesets the same way we use libraries. E.g. drag and drop, typeahead, etc. Rulesets can be authored by the community. Precept can add have its own rulesets that can be turned on or off when defining a session.

#### Dev tools
Because Clara's sessions are immutable, we can store each one and cycle through them. Clara provides tools for inspecting sessions that show what rules fired, why they fired, what facts were inserted, which were retracted, which rule inserted what, which rule retracted what, and so on.

In addition, changes to Precept's view model can visualized and tracked just like [Redux DevTools](https://github.com/gaearon/redux-devtools).

#### General purpose algorithms

Precept aims to enable teams to build increasingly game-like UIs. This sometimes requires algorithms for path-finding, tweening, collision detection, and distance calculation. We want to write applications where talking about these things is trivial. That means never having to fall back to imperative programming, while at the same time having the performance it provides. We're working to support declarative statements like `(<- ?my-trip (distance ?paris ?london))` that allow us to focus on what, not how, by calling performant, general-purpose algorithms under the covers.

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

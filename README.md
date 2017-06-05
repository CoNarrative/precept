![Precept](https://github.com/CoNarrative/precept/blob/master/precept-logo%401x.png)

A declarative programming framework

[![Clojars Project](https://img.shields.io/clojars/v/precept.svg)](https://clojars.org/precept)
[![CircleCI](https://circleci.com/gh/CoNarrative/precept.svg?style=shield&circle-token=b23498670888edf670832326d50f9d8fab60b2e3)](https://circleci.com/gh/CoNarrative/precept)
[![license](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/CoNarrative/precept/blob/master/LICENSE)
[![discord](https://img.shields.io/badge/discord-%23precept%20%40%20clojurians-7bb6b9.svg)](https://discord.gg/5GXQwrY)

[Docs](https://conarrative.github.io/precept/)
| [Example](https://github.com/CoNarrative/precept/tree/master/src/cljs/precept/todomvc)
| [Issues](https://github.com/CoNarrative/precept/issues)
| [Roadmap](https://github.com/CoNarrative/precept/projects/1)
| [Contributing](https://github.com/CoNarrative/precept/blob/master/CONTRIBUTING.md)


> "You need only specify what you require, not how it must
be achieved." - [Out of the Tar Pit](http://shaffner.us/cs/papers/tarpit.pdf)

## What it is and why
Precept is an app framework for writing reactive applications using declarative rule-oriented logic and relational data modeling.

We built it because we want to build progessively large, intricate apps using clear and minimalistic units of lego-like code.  

No code style is more clear and concise than simply declaring application logic. *Computations* within functional reactive frameworks offer this in part, but we found stream, store, and object-oriented reactive models too artificial. 
They make us think too much over the wrong things. We pre-arrange all of our data before we know how it will be used, and then rearrange our code and computations as data locations and access points change over the course of development.

Instead we wanted a framework where we could add new data about the world into a "bag" without worrying about its location in a universal object graph, and then just state our application logic. 
It should automatically derive computations and receive events *from any combination of data in our state* without any hand-wiring or bookkeeping on our part.

Rule engines operating over facts do exactly that; the Clara rule engine is the foundation of Precept.

## How it works
There are facts and there are rules. Facts are data, and rules are declarative statements about that data. All application state is represented by facts, and all application logic is expressed with rules.

Facts have three parts: an entity id, an attribute, and a value. They model data relationally and are expressed with Clojure vectors.

Here's an example fact:
```clj
[123 :todo/title "Use Precept"]
  |         |         |
  |         |         |
  e         a         v
```
> *"Thing 123 has the attribute `:todo/title`, the value of which is 'Use Precept'."*

Rules have two parts: the conditions and the consequences. Conditions state what facts must be true for the consequence to happen. They are expressed using pattern matching. Consequences are **any valid Clojure or Clojurescript code**. They have full access to all variables that were matched in the rule's condition.

Here's an example rule:

```clj
(rule todo-is-visible
  [[_ :visibility-filter :active]]
  [[?e :todo/done false]]
  =>
  (println "The todo with this entity id is visible: " ?e)
```
> *"When visibility filter is "active todos" and there's a todo that's not done, print the todo's id."*

Instead of printing a message, what if we wanted to make the todo visible? The consequence of the rule could be a new fact:

```clj
...
=>
(insert! [?e :todo/visible true]))
```

Whenever a fact is inserted, other rules have a chance to respond to it.

Suppose you also had this rule:

```clj
(rule print-visible-titles
  [[?e :todo/visible true]]
  [[?e :todo/title ?title]]
  =>
  (println "Visible todo title: " ?title))
```
> *"When there is a visible todo, get its title, then print it."*


This rule will attempt a join on entity id between a title fact and a visible fact, meaning it will try find a title whose entity id matches the entity id of a visible todo.

For our rules to do anything, we need to add some facts.

A `session` is a container for rules and facts. Given a session and an optional set of initial facts, Precept makes the resultant state available to your views.

```clj
(defn todo [title done]
  (let [id 123]
    [[id :todo/title title]
      id :todo/done false]]))

(defn visibility-filter [kw]
  [(uuid) :visibility-filter kw])

(session my-session 'my-ns.rules)     

(start! {:session my-session :initial-facts [(todo "Use Precept")
                                             (visibility-filter :active)]})

=> The todo with this entity id is visible: 123
=> Visible todo title: Use Precept
```

## Subscriptions

Components subscribe to queries and rerender whenever the query result changes. Because query subscriptions are defined as rules, they enjoy the same performance benefits as rules. Namely, they are not rerun if there is no possible way their results could have changed. See [Rete algorithm](#rete-algorithm).

Here's a subscription definition for a visible todo:
```clj
(defsub :visible-todos
  [[?e :todo/visible true]]
  [(<- ?todo (entity ?e))]
  =>
  {:visible-todo ?todo}
```

Components subscribe to the keyword defined by `defsub`:
```clj
(defn visible-todo []
  (let [{:keys [db/id todo/title todo/done]} @(subscribe [:visible-todo])]))
    [:div title done]
```

Precept converts all its subscription results to maps, making the work of rendering data in the view layer the same as other frontend libraries and frameworks.


## Inserting facts

Views insert new facts into the session with `then`. An `:on-change` handler that inserts a fact about a todo edit looks like this:
```clj
[:input
  {:on-change #(then [id :todo/edit (-> % .-target .-value)])}]
```

Precept will modify the value of `:todo/edit` on each keypress by retracting the previous fact and inserting the new one. This is because Precept enforces one-to-one cardinality for all facts by default (i.e., for any entity-attribute pair, one value can exist at any point in time). In this case that means there can't be more than one value for entity 123's `:todo/edit`.

## Application patterns
While we do not enforce any structure on your rule session, there are some common patterns.

First, application state will consist of "base state" and "derived state". Both are identical-appearing facts in the session.

Base state may be data from the server, local data, or semi-persistent data about view concerns (active selection, collapsed, etc.).

Mutating base state is done outside of rules using `then`, or from within a rule consequence using `insert-unconditional!`.
Here is an example of an "action handler" that mutates the state in response to the user surfacing a `:mark-all-done` intent:

```clj
(rule complete-all
  {:group :action}
  [[_ :mark-all-done]]
  [[?e :todo/done false]]
  =>
  (insert-unconditional! [?e :todo/done true]))
```

Derived (or computed) state is everything else, from intermediate calculations all the way to property bags for view rendering.

Derived state (by necessity) is only expressible within the consequence of a rule using `insert!`

```clj
(rule define-visibility-of-todo
  [:or [:and [_ :visibility-filter :all] [?e :todo/title]]
       [:and [_ :visibility-filter :done] [?e :todo/done true]]
       [:and [_ :visibility-filter :active] [?e :todo/done false]]]
  => (insert! [?e :todo/visible true]))
```

`define` is a more concise way to do the same thing:

```clj
(define [?e :todo/visible true] :-
  [:or [:and [_ :visibility-filter :all] [?e :todo/title]]
       [:and [_ :visibility-filter :done] [?e :todo/done true]]
       [:and [_ :visibility-filter :active] [?e :todo/done false]]])
```

Note that derived state calculations can use both base and other derived state as needed; facts are facts whether inserted through mutation or derived.

Subscriptions are (optionally) used to accumulate state (both base and derived) for view rendering.

## Schema support
Precept enforces cardinality and uniqueness according to Datomic-format schemas. If we wanted entities to have multiple values for the `:todo/edit` attribute, we can specify a one-to-many relationship for it:

```clj
(attribute :todo/edit
  :db/cardinality :db.cardinality/one-to-many)
```

One-to-many attributes can then be returned to components as a list.

```clj
;; Tuple format  
[[123 :todo/edit "H"]
 [123 :todo/edit "He"]
 [123 :todo/edit "Hey"]]

;; Map format
{:db/id 123 :todo/edit ["H" "He" "Hey"]}
```

Unique attributes are handled using the same semantics as Datomic for [:db.unique/identity](http://docs.datomic.com/identity.html#sec-4) and [:db.unique/value](http://docs.datomic.com/identity.html#sec-5). When there is a conflict, instead of throwing an error, Precept inserts facts about the error into the session so they it may be handled or resolved through rules.

Precept supports both a `:db-schema` and a `:client-schema`. This allows you to easily access to facts you want to persist, while still allowing the schema definitions for your client-side only data. Client and db schemas are both enforced the same way.

## Differences from Datomic

Like Datascript, Redux, re-frame, and other front-end data stores, Precept does not attempt to maintain a history of all facts. We agree with Datomic's design with respect to this, but because Precept runs in a web browser, space is relatively limited. For our application, we see more value in that space being occupied by tens of thousands of facts that represent the current state of the world instead of e.g. 100 facts with 100 histories.

## Rete algorithm

> “In the ideal world, **we** are not concerned with
performance, and our language and infrastructure provide all the general
support we desire.” - Out of the Tar Pit, emphasis added

Precept wraps
[Clara](http://www.github.com/cerner/clara-rules), a ground-up implementation
of the Rete algorithm in Clojure and Clojurescript.  

Rule engines are not usually written with the browser in mind. Clara is perhaps the first. A declarative approach to front-end web development would not be feasible without their work: Writing declarative code requires the algorithms that underlie it to be performant.

The Rete algorithm creates a network of nodes for each rule. Nodes are indexed, may be shared, and contain memories of the values they match. Overall, Rete trades space for time, and calculates incremental changes with almost no effort. This solves the vast majority of situations on the front-end where performance becomes a consideration. Keeping a filtered list of n items does not require the entire list to be iterated through and recalculated when a new item is added.

## Where we're headed

The [project board](https://github.com/CoNarrative/precept/projects/1) contains the most up-to-date information about what features are being discussed, prioritized, and worked on. Here's a few we're excited about.

#### Rendering views

Using a rule engine allows us to know exactly what changes from one state to the next. This means we don't need React's diff algorithm or the concept of subscriptions. If we declare views as the consequences of rules, we can automatically point update them when the facts they care about change.

#### Ruleset API

We want to use general purpose rulesets the same way we use libraries. E.g. drag and drop, typeahead, etc. A Ruleset API is in the works to make it easy for the community to write pluggable sets rules that application authors can integrate seamlessly with their own.

#### Dev tools
Because Clara's sessions are immutable, we can store each one and cycle through them. Clara provides tools for inspecting sessions that show what rules fired and why, what facts were inserted and by what rule, which were retracted, and so on.

In addition, changes to Precept's view model can visualized and tracked just like [Redux DevTools](https://github.com/gaearon/redux-devtools).

#### General purpose algorithms

Precept aims to enable teams to build increasingly game-like UIs. This sometimes requires algorithms for path-finding, tweening, collision detection, and distance calculation. We want to write applications where talking about these things is trivial. That means never having to fall back to imperative programming, while at the same time having the performance it provides. We're working to support declarative statements like `(<- ?my-trip (distance ?paris ?london))` that allow us to focus on what, not how, by calling performant, general-purpose algorithms under the covers.

### Thanks
- [Clara](http://www.clara-rules.org/)

- [Datomic](http://www.datomic.com/)

- [Datascript](https://github.com/tonsky/datascript)

- [re-frame](https://github.com/Day8/re-frame)

- [reagent](https://reagent-project.github.io/)

- [Mike Fikes](http://blog.fikesfarm.com/)

- [Dmitri Sotnikov](https://yogthos.net/index.html)
  and the [Luminus](http://www.luminusweb.net/) framework

- [Bruce Hauman](http://rigsomelight.com/) and
  [Figwheel](https://github.com/bhauman/lein-figwheel)

- [Cursive](https://cursive-ide.com/)

- [Precursor](https://github.com/PrecursorApp/precursor)

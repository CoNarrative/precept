## Application Organization

Precept is agnostic on how you structure your application flow. 
But the ability to add arbitrary facts at will and write rules that can easily find and respond to them provides an overwhelming number of options, and that's 
difficult to navigate when you're just starting out.

Here are some patterns we ourselves use that we hope will help you get started:

## A single state transition


 1. **A state transition begins when a new fact is inserted from outside the rule context using `then`.**
    `then` is equivalent to the consequence of a rule. Here, the condition, or "when", is typically some kind of event: things like an `:on-click`, a REST response, or a message from the server. 
    Ultimately, `then` inserts new facts into the session and fires your rules. It does have one special characteristic: It queues the `insert` for the next session firing. 
    This means that when you use it to insert facts within the consequence of a rule, your rules won't know about what you inserted until the next go around. By contrast, `insert-unconditional!` would do the exact same thing as `then` in this case except rules will be able to respond to the fact you inserted immediately.
    
    In other frameworks, `then` may be called `dispatch`. It is perfectly reasonable to think about `then` as a version of that.

 2. **Rules with `{:group :action}` as their first argument may be used to "intercept" any new fact entering the session.**
    Rules in the `:action` group are given first priority to respond to new facts. We use such rules as "action handlers" to intercept and respond to facts inserted from outside the session by `then`. Rules in this group may do the same with facts inserted from inside the session by lower priority rules, though we do not generally recommend this. 
    Apart from their high salience, `{:group :action}` rules are like any other: They may retract facts, insert facts, and/or fire side-effects. 

    Action handlers should almost always use `insert-unconditional!` instead of `insert!`. 
    This is because any facts added with `insert!` are automatically retracted when the conditions under which they were inserted become false, and you will likely want to remove the "action" fact. 
    Otherwise, it would persist, and the same consequence would happen the next time the rules fire.
    In other rules libraries, `insert!` is known as "insert logical". 
    You can read more about the differences between Clara's `insert!` and `insert-unconditional!` [here](http://www.clara-rules.org/docs/truthmaint/).
    
    There are multiple ways to retract action facts. 
    Our preferred method is to use the entity id `:transient` for any fact that is an event or action and should only survive only one session. 
    Precept removes all facts with this eid at the end of every session. 
    You may implement your own equivalent "cleanup" rules that are given the lowest priority and fire at the end of the session by using `{:group :cleanup}`.
    You may also retract the fact that represents the action or message from within the action handler itself. 

 3. **Other rules fire and update "derived" state.**
    Rules have the default precedence level of `:calc`. These are not as active as "action" group rules. Most rules fall into this category because 
    they take the "base" facts (inserted from the outside world, or via an action handler), and derive other facts from them.

 4. **Subscription rules gather facts to be consumed by the view.**
    Subscription handlers receive lowest priority and typically fire once all derived calculations have been performed. Subscriptions are defined using `defsub`. Its name corresponds to the keyword for the subscription it fulfills (e.g. `(defsub :my-subscription ...)`). It returns a map as its consequence that may contain bound variables for the result of the subscription.

 5. **The view model updates.** 
    When the rules are done firing, Precept updates the reactive view model with the subscription results and all other facts in the session

 6. **Subscribed components rerender if a query they subscribe to produced a new result.**
    Components update through named subscriptions. 
    A subscription is simply a Reagent cursor/lens that watches the subscription's location on a Reagent atom for changes.
    The format for a subscription follows `re-frame`'s syntax: `@(subscribe [:my-subscription])`. 
    Precept has no parameterized subscriptions. The vector around `:my-subscription` is unnecessary now but may help support this in the future.
    Subscriptions are defined with `defsub` as mentioned above.

## Redux actions vs. inserting facts vs. inserting `:transient` facts
Transient facts act like an event, action, or message in frameworks like Redux and re-frame. 

The pattern of state management popularized by Redux and others mandates that state mutations can only take place through "actions". 
In these frameworks, actions are functions that dispatch a message containing the name of the action and its payload. 
The messages picked up by a "reducer", which reads the contents of the message and applies the appropriate transformation to the global state atom.
We tried following this pattern initially. It added extra layer of code and overhead that didn't seem to have any benefit. 
We found we could achieve the same result by allowing facts to be inserted directly into the session. 
So far, nothing has blown up as a result of mutating state without ceremony. 
When things change, there simply new facts. 
The name of a message that brought them into being isn't always relevent information. 
There aren't always transformations we need to perform.
Actions _are necessary_ sometimes. But using them for everything results in accidental complexity.

When we need to, we can insert facts that are not "about the world" so much as an action to be processed by some rule. 

```clj
:on-click #(then [:transient :deselect-all true])

(rule clear-completed
  {:group :action}
  [[_ :deselect-all true]]
  [?selected <- [_ :product/selected]]
  =>
  (retract! ?selected))
```
> When deselect all is true and there's a product that's selected, remove it.

*Any* fact coming from the outside world may be intercepted by a rule and removed or added to before downstream rules have a chance to process it.

```clj
:on-click #(then [123 :ultimate-truth "potatoe"])

(rule intercept-and-change
  {:group :action}
  [?wrong <- [?e :ultimate-truth "potatoe"]]
  =>
  (insert-unconditional! [?e :ultimate-truth 42])
  (retract! ?wrong))

(rule check-for-potatoe-facts
  {:group :calc} ; For example purposes. Rules default to `:calc` group
  [[?e :ultimate-truth "potatoe"]]
  =>
  (println "I shouldn't do anything"))
```

Note that you can have more than one rule to handle the same action type by simply having narrower pattern matching.
That makes it very easy to set up Finite State Machine patterns and selective handling of actions based on context or content.

Here's the same action that only completes all to-dos when in an `idle` state:
```clj
(rule clear-completed
  {:group :action}
  [[_ :app-state :idle]]
  [[_ :clear-completed]]
  [[?e :todo/done true]]
  [(<- ?done-entity (entity ?e))]
  =>
  (retract! ?done-entity))
```


## Side effects
Side-effects are handled the same way -- watch for the right pattern coming through the `:transient` pipeline and respond in the consequence.

```clj
(rule mutate-locally-and-notify-server-on-clear-completed
  {:group :action}
  [[_ :clear-completed]]
  [[?e :todo/done true]]
  [(<- ?done-entity (entity ?e))]
  =>
  (retract! ?done-entity)
  (api/clear-completed))
```

Note that we don't feel the need to artificially separate out mutations from side-effects into different rules when the rule conditions would be the same - that's your call.

But if the side-effects don't follow the same shape, we'd keep it separate. 
Here's an example where we notify the server on any clear-completed command (to log it) while only mutating state if the app is in the idle state and the item is done:

```clj
(rule notify-server-on-clear-completed
  {:group :action}
  [[_ :clear-completed]]
  =>
  (api/clear-completed))
```

```clj
(rule mutate-locally-on-clear-completed
  {:group :action}
  [[_ :app-state :idle]]
  [[_ :clear-completed]]
  [[?e :todo/done true]]
  [(<- ?done-entity (entity ?e))]
  =>
  (retract! ?done-entity))
```

There is nothing stopping you from triggering a side-effect from a derived calculation either — you just might want to insert a new `:transient` action and let it go through a pipeline though to keep things clear.

## Backend
We're agnostic on back-end data and communications except for always using rules to manage them.
If we are receiving server messages (e.g. from SSE) we can put them directly into the session as `:transient` and let rules handle them just like user commands.
If we are handling a REST callback, we might do some pre-processing of the data beforehand.

We'll insert into the session what we need to handle the response via rules just as if it were a normal action - the status code, the data, etc.

Example:
```clj
(rule insert-on-rest-response
 [[_ :clear-cart]]
 =>
 (api/clear-cart 
  {:handler #(then [:transient :cart-cleared? true])
   :error-handler (fn [res] 
                   (then [:transient :cart-cleared? false]
                         [:transient :server/response-code (:status res))
                         [:transient :server/message (:message res))]))})
                         
```

## Derived calcuations
The beauty of the rule engine is that it keeps all your derived logic efficiently updated.

Derived state (by necessity) is only expressible within the consequence of a rule using `insert!`.

```clj
(rule define-visibility-of-todo
  [:or [:and [_ :visibility-filter :all] [?e :todo/title]]
       [:and [_ :visibility-filter :done] [?e :todo/done true]]
       [:and [_ :visibility-filter :active] [?e :todo/done false]]]
  =>
  (insert! [?e :todo/visible true]))
```

`define` can be a more concise and explanatory way to do the same thing. It does away with the rule name and is instead defined by its consequence. The ordinary rule order is therefore flipped; conditions are second:

```clj
(define [?e :todo/visible true] :-
  [:or [:and [_ :visibility-filter :all] [?e :todo/title]]
       [:and [_ :visibility-filter :done] [?e :todo/done true]]
       [:and [_ :visibility-filter :active] [?e :todo/done false]]])
```

Because the `:visibility-filter` happens to be mutually exclusive, this is also equivalent:
```clj
(define [?e :todo/visible true] :- [[_ :visibility-filter :all] [?e :todo/title]])

(define [?e :todo/visible true] :- [[_ :visibility-filter :done] [?e :todo/done true]])

(define [?e :todo/visible true] :- [[_ :visibility-filter :active] [?e :todo/done false]]])
```

How does this work? 
Suppose `[123 :todo/visible true]` exists and was inserted because `:visibility filter` was `:done`.
When the visibility filter changes to `:active`, all `:todo/visible` facts that were `insert!`ed as a result of the rule with `:visibility-filter :done` are retracted (`:visibility-filter :done` is now false).
This is because one of their reasons for existing in the first place has become false. 
In this way, rules with an `insert!` consequence mean "if and only if", or "a is true when b and false otherwise", "a exists when b and false otherwise".
`:visibility-filter :active` is true now though, so for any todo that isn't done (`[?e :todo/done false]`), 
the rule inserts a fact about it being visible.

By contrast, facts inserted with `insert-unconditional!` depend on the truth of nothing. 
"a is true", "a exists", "I stipulate it is the case that a!". 
Unconditially-inserted facts exist unless they are explicitly told not to. 
They can only be removed by `retract!`ing them in a rule's consequence.

## Base state v. derived state
It is sometimes helpful to think of state in two categories. Internally, we call them "base" and "derived", but there are other names for them ("ground", "computed", "essential", etc.). 
Derived state comes from base state. 
Base state comes from `then`, `insert-unconditional!` or the initial facts you add to the session. 
These are the facts upon which all higher-level abstractions are built.  
Considering `insert!` and `insert-unconditional!`, we were dancing around the concept of "base" facts and "derived" facts. 
In short, you should tend toward `insert-unconditional!` or `then` for base state, and `insert!` for facts derived from that base state.
The `:todo/visible` fact from the previous section is an example of derived state. 
We don't insert todo items with `:todo/visible true`. 
Given the base state of `:visibility-filter` and a todo's done status, we can derive whether it is visible.
 

## View layer

Our React components subscribe to lenses across the Reagent ratom and update accordingly.

We've taken an opinionated stance on Reagent and React. 
This is what we currently use internally.
Having received valuable feedback from the community, we will be opening up Precept's API to allow you to implement a view layer that works for you. 

To us, Precept has a greater opportunity here. We believe we can use rules to render views directly. 
Not only should this make writing applications easier, it should be abstracted enough to support arbitrary rendering targets (DOM, svg, canvas, WebGL, or any scene graph)
using the same code.


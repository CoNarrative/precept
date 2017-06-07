##Application Organization

Precept allows you to code UI logic using a declarative rules engine and Datomic-style tuple data structure.

It is agnostic on exactly how you actually structure your app or your app flow or communicate with your back-end (if there is one). That said, here's how we ourselves do it:

##Flow
A cycle begins with either an *action* from the user or backend, or a *tick* in the case of a game loop.

We'll cover the *action*-driven flow as it is more common, but we're finding that precept is great for synthetically deriving actions from low-level state information (such as mouse position and button up/down) in a game loop.

That flow in short is:

 1. Insert action intent fact into session (user command, REST response, server message)
 2. Action handling rules mutates "base" state facts and fire side-effects
 3. Calculation rules update "derived" state facts
 4. Precept infrastructure updates component view model (data map for React, etc.) through a subscription lens on an observable atom
 5. Component is updated as a result (managed by Reagent, etc.)

Since we're minimalists, we'll also just sometimes mutate directly from the view instead of bothering with an action.

## Actions
Actions for us are "action intents" - just messages from the view in response to user activity or messages from the server.

Since we handle only one action at a time, we insert action facts using a `:transient` entity id (you could call it `:current-action`).

The action can be inserted from a view, from a REST callback, or a server-side message.

Example:
```clj
[:button#clear-completed {:on-click #(then [:transient :clear-completed true])}
        "Clear completed"]
```

## Mutations
In response to an action or message, we'll most often mutate state in one or more "action handling" rules.

That is, we pattern match the action fact in the condition and then `insert-unconditional!` and/or `retract!` in the consequence.

Note that `insert-unconditional!` makes a "one-time" mutation, whereas facts inserted using `insert!` will be auto-managed by the rule engine. 

Example:

```clj
(rule clear-completed
  {:group :action}
  [[_ :clear-completed]]
  [[?e :todo/done true]]
  [(<- ?done-entity (entity ?e))]
  =>
  (retract! ?done-entity))
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
  (retract! ?done-entity))
  #make API call here...will be triggered for each retracted fact
```

Note that we don't feel the need to artificially separate out mutations from side-effects into different rules when the rule conditions would be the same - that's your call.

But if the side-effects don't follow the same shape, we'd keep it separate. 
Here's an example where we notify the server on any clear-completed command (to log it) while only mutating state if the app is in the idle state and the item is done:

```clj
(rule notify-server-on-clear-completed
  {:group :action}
  [[_ :mark-all-done]]
  =>
  #make API call here...
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

There is nothing stopping you from triggering a side-effect from a derived calculation either - you just might want to insert a new :transient action and let it go through a pipeline though to keep things clear.

## Backend
We are completely agnostic on back-end data & communications except for always using rules to manage them.
If we are receiving server messages (e.g. from SSE) we just put them directly into the session as `:transient` and let rules handle them just like user commands.
If we are handling a REST callback, we might do some pre-processing of the data (especially if JSON) before putting it into the session.

We'll insert into the session what we need to handle the response via rules just as if it were a normal action - the status code, the data, etc.

Example:



## Derived calcuations
The beauty of the rule engine is that it keeps all your derived logic efficiently updated.

Derived state (by necessity) is only expressible within the consequence of a rule using insert!

(rule define-visibility-of-todo
  [:or [:and [_ :visibility-filter :all] [?e :todo/title]]
       [:and [_ :visibility-filter :done] [?e :todo/done true]]
       [:and [_ :visibility-filter :active] [?e :todo/done false]]]
  => (insert! [?e :todo/visible true]))


We added a little sugar called `define` to be a more concise/explanatory way to do the same thing:

(define [?e :todo/visible true] :-
  [:or [:and [_ :visibility-filter :all] [?e :todo/title]]
       [:and [_ :visibility-filter :done] [?e :todo/done true]]
       [:and [_ :visibility-filter :active] [?e :todo/done false]]])

Note that derived state calculations can use both base and other derived state as needed; facts are facts whether inserted through mutation or derived.

## Subscriptions

Subscriptions are (optionally) used to accumulate state (both base and derived) for view rendering. Right now that works through a Reagent ratom.

## View

Our React components subscribe to lenses across the Reagent ratom and update accordingly.

We're using Reagent/React right now ourselves. We're looking into decoupling that and may even do something more radical with the view rendering such as making it entirely rule-based.

What we're really excited about with Precept is not so much the view or the view coupling but how the declarative approach lets us manage complex app state in a simple logical extensible manner.
<!-- markdown-toc start - Don't edit this section. Run M-x markdown-toc-refresh-toc -->
**Table of Contents**

- [Quick examples](#quick-examples)
    - [Rules](#rules)
    - [Facts](#facts)
- [Specification](#specification)
    - [Variables](#variables)
    - [Special forms and symbols](#special-forms-and-symbols)
        - [`_` : symbol](#--symbol)
        - [`<-` : symbol](#---symbol)
        - [`<-` : macro](#---macro)
        - [`entity` : macro](#entity--macro)
        - [`entities` : macro](#entities--macro)
- [Expressions](#expressions)
    - [Tuple expression](#tuple-expression)
        - [EAV](#eav)
        - [Attribute only](#attribute-only)
        - [Value omitted](#value-omitted)
    - [Fact binding expression](#fact-binding-expression)
    - [:test expression](#test-expression)
    - [S-expression predicates](#s-expression-predicates)
    - [Accumulator expression](#accumulator-expression)
    - [Boolean operations](#boolean-operations)
        - [`:or`](#or)
        - [`:and`](#and)
        - [`:not`](#not)
        - [`:exists`](#exists)
- [Inserting facts](#inserting-facts)
    - [`insert!`](#insert)
    - [`insert-unconditional!`](#insert-unconditional)
    - [`insert`](#insert)
- [Retracting facts](#retracting-facts)
    - [`retract!`](#retract)
    - [`retract`](#retract)
- [Examples](#examples)
    - [Matching a value](#matching-a-value)
    - [Binding a value to a variable](#binding-a-value-to-a-variable)
    - [Binding a fact to a variable](#binding-a-fact-to-a-variable)
    - [Join on id](#join-on-id)
    - [Join on value](#join-on-value)
    - [Referential join](#referential-join)

<!-- markdown-toc end -->

# Quick examples

## Rules

```clj
(rule clear-cart-on-command ; <-- rule name
  {:group :action} ; <-- salience group (optional)
  [[_ :clear-cart]] ; <-- tuple expression with ignored first slot
                    ;     and value slot omitted
  [?item <- [_ :cart-item/product-id]] ; <-- expr. with fact binding (?item)
  => ; <-- separator
  (retract! ?item)) ; <-- right-hand side / consequence
  ```

```clj
(rule add-item-to-cart-when-not-in-cart-on-command
  {:group :action}
  [[_ :add-to-cart ?product-id]] ; <-- variable binding in value slot
  [:not [_ :cart-item/product-id ?product-id]] ; <-- boolean operator
  =>
 (api/add-to-cart {:db/id (random-uuid) ; <-- entity map
                   :cart-item/product-id ?product-id
                   :cart-item/quantity 1}))
```


## Facts

Vector representation:
```clj
[[id :cart-item/product-id 123]
 [id :cart-item/quantity 1]])
```

Map representation:
```clj
{:db/id id
 :cart-item/product-id 123
 :cart-item/quantity 1}
```

Record representation (internal):
```clj
[(->Tuple id :cart-item/product-id 123 1)
 (->Tuple id :cart-item/quantity 1 2)]
```


# Specification

## Variables
`?<symbol-name>`
- First reference: binds a value to the variable
- Subsequent references: variable contains the value

Example:

```clj
; ?e binds to eid of a fact with attribute :color, value "blue"
[[?e :color "blue"]] 
; ?e has been assigned a value. Expression uses the value 
; captured from the previous expression when finding a match
[[?e :shape "circle"]]
```

## Special forms and symbols

### `_` : symbol

`[[_ keyword? ?any]]`

Ignores `e` or `v` slot within a tuple expression. May not be used to ignore an attribute.

### `<-` : symbol

`[<variable> <- <expression>]`

Binds result of expression to variable. 

Usage:

``` clj
[?my-color-fact <- [_ :color "blue"]]
```

``` clj
[?all-color-facts <- (acc/all) :from [_ :color]] 
```


### `<-` : macro

`[(<- <variable> <special-form>)]`

Binds a variable to the result of a special form

Usage:

``` clj
(:require-macros [precept.dsl :refer [<- entity]])
...
[[?e :color "blue"]]
[(<- ?blue-entity (entity ?e)]]
```

### `entity` : macro

`[(<- <unbound-variable> (entity <bound-variable=eid>))]`

Accumulates all facts for a given entity id.

``` clj
(:require-macros [precept.dsl :refer [<- entity]])
...
[[?e :color "blue"]]
[(<- ?blue-entity (entity ?e)]]
```

### `entities` : macro
`[(<- <unbound-variable> (entities <bound-variable=[eids]>))]`

Accumulates all facts from a collection of entity ids.

``` clj
(:require-macros [precept.dsl :refer [<- entities]])
...
[?eids <- (acc/all :e) :from [_ :color "blue"]]
[(<- ?blue-entities (entities ?eids)]]
```

# Expressions

## Tuple expression

Positionally match all or some of a fact's fields.


### EAV

 [`any?` `keyword?` `any?`]

### Attribute only

 [`keyword?`] | [`_` `keyword?`]
 
### Value omitted

 [`any?` `keyword?`] | [`_` `keyword?`]
 

## Fact binding expression

`[<unbound-variable> <- <tuple-expression | accumulator-expression>]`

Binds a fact to a variable. Binds a list of facts if expression is an accumulator.

Example:

``` clj
[?my-fact <- [_ :color "blue"]]
```

## :test expression

`[:test <predicate>]`

Test expressions may be used to specify an arbitrary predicate as part of the rule's condition.

Example:

``` clj
[[_ :high-temp/yesterday ?x]]
[[_ :high-temp/today ?y]]
[[_ :high-temp/tomorrow ?z]]
[:test (> (max ?x ?y ?z) 100)]
```

## S-expression predicates

Arbitrary s-expressions predicates may be used in the value slot.

Examples:
``` clj
[[?e :number (> 42 ?num)]]
```

``` clj
(def magic-number 42)
(defn my-func [x] 
  (and (number? x)
       (> (+ 1 x) magic-number)))
...
[[?e :number (my-func ?num)]]
```

## Accumulator expression

`[<variable> <- <accumulator-function> :from <tuple-expression>]`

See [`precept.accumulators`](https://conarrative.github.io/precept/precept.accumulators.html) namespace for list of predefined accumulator functions.

Example:

``` clj
[?names-of-colors <- (acc/all :v) :from [_ :color]]
```

## Boolean operations

### `:or`
`[:or <expression> <expression> ...]`

Logical or.

May nest or be nested within other boolean expressions.

### `:and`

`[:and <expression> <expression> ...]`

Logical and.

May nest or be nested within other boolean expressions.

Because expressions contain an implicit logical and, reserve for cases where a logical and must be explicit.

Usage:

``` clj
[:or [:and [_ :color "blue"]
           [_ :shape "circle"]]
     [:and [_ :color "yellow"]           
           [_ :shape "triangle"]]]
```

### `:not`

`[:not <expression>]` 

Logical not. 

May nest or be nested within other boolean expressions.

Usage:

``` clj
[[?e :shape "circle"]]
[:not [?e :color "blue"]]
```

``` clj
[:not [:and [_ :color "blue"]
            [_ :shape "circle"]]]
```

### `:exists`

`[:exists <expression>]`

True if the wrapped expression has at least one match.

Usage:

``` clj
[[?e :shape ?triangle]]
[:exists [?e :color "blue"]]
```

# Inserting facts
## `insert!`

`(insert! <entity-vector | entity-map | Tuple>)`

Argument may be a collection of one supported type.

For use in the consequence block of a rule only.

Equivalent of [insert logical](https://docs.jboss.org/drools/release/5.2.0.Final/drools-expert-docs/html/ch03.html). Inserts facts that exist so long as the conditions under which they were inserted are true.

## `insert-unconditional!`
`(insert-unconditional! <entity-vector | entity-map | Tuple>)`

Argument may be a collection of one supported type.

For use in the consequence block of a rule only.

Inserts facts that exist until they are manually removed.

## `insert`

`(insert! <session> <entity-vector | entity-map | Tuple>)`

For use outside of a consequence block. 

Inserts facts that exist until they are manually removed.

Takes a session as first argument. Second argument may be a collection of one supported type.


# Retracting facts

## `retract!`

`(retract! <Tuple>)`

Removes fact from session.

For use in the consequence block of a rule only.

Argument may be a collection of Tuple record instances. Requires fact instance to retract.

Example:

``` clj
[?blue-fact <- [_ :color "blue"]]
=>
(retract! ?blue-fact)
```
``` clj
[?blue-facts <- (acc/all) :from [_ :color "blue"]]
=>
(retract! ?blue-facts)
```

## `retract`

`(retract <session> <Tuple>)`

Removes fact from session.

For use outside of a consequence block. 

Second argument may be a collection of Tuple record instances.


# Examples

## Matching a value

When `:color` is `"blue"`:

```clj
[[_ :color "blue"]]
```
Value slot can be anything. 

Equality determined by Clojure's [`=`](https://clojuredocs.org/clojure.core/=) function.

## Binding a value to a variable

When a fact with attribute `:color`, bind its value to `?color`:

```clj
[[_ :color ?color]]
```

Creates variable `?color` and binds it to the value in the value slot.

## Binding a fact to a variable

When a fact with attribute `:color`, bind the entire fact to `?my-fact`:

```clj
[?my-fact <- [_ :color ?color]]
```

- `?color` is still available as a bound value
- `?my-fact` is returned as a `Tuple` record instance
## Join on id

Obtains when an entity with the same eid has `:color "blue"` and `:shape "circle"`.

``` clj
[[?e :color "blue"]]
[[?e :shape "circle"]]
```

## Join on value

Obtains when a `:temperature/current` fact and `:temperature/record-high` fact 
have the same value.

``` clj
[[_ :current-temperature ?v]]
[[_ :really-hot-temperature ?v]]
```

## Referential join

Fact values may hold references to other facts.

``` clj
[[_ :node/parent ?node-id]]
[[?node-id :node/name ?name]]
```


# Precept full stack example

In two terminals:
1. `lein run`
2. `lein figwheel`

The first command starts an http-kit server on port 3000. The second will build the client. The app will be visible at http://localhost:3000.

There is an nREPL server on 7000 for the server and 7002 for the client (be make sure to call `(cljs)` in the client REPL before attempting to evaluate any Clojurescript code).

## The server
The server is meant to be minimal. It shows how Precept can 1. interact with a REST interface 2. update reactively when data is pushed to it (in this case via socket). 

## The client
The client uses reagent for the view, cljs-http for making REST calls, and sente for the socket connection.

There is little magic in what follows. Any result obtained or process described is simply the application of rules to facts unless otherwise stated. In other words, we are describing an implementation of the application, not the framework itself.

When the client loads, a single fact (`[:transient :start true]`) is inserted. We use this to represent a "start" state. 
We have written a rule rule matches on it and makes REST calls to fetch the cart and a list of products from the server. Because its eid is `:transient`, there is some magic here, as Precept auto-retracts any fact with the eid of `:transient` at the end of every session, ensuring such facts survive one rule firing only. 

All API calls are defined in `api.cljs`. Their response handlers are wired more-or-less directly to Precept's `then` function, which behaves exactly like an `insert-unconditional!` inside a rule's consequence. When a response from the server is received, facts are inserted into the session, rules fire, state advances, and views update.

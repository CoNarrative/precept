#!/usr/bin/env bash

# Starts `planck` self-hosted CLJS repl on localhost:7777 with lein classpath loaded
#
# To use with Cursive/IntelliJ REPL:
# 1. Create configuration for local / clojure-main
# 2. Enter the following at the REPL prompt (assumes tubular is a dependency)
#   (require 'tubular.core)
#   (tubular.core/connect 7777)
# 3. In REPL window use dropdown to change clj repl to cljs repl


planck -c`lein classpath` -n 7777
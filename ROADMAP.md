# Roadmap

In brief and no particular order at the moment:
- `deflogical` - macro that reverses LHS and RHS of rule and
will eventually provide truth maintenance functionality
- `q` - Easier way to access query interface, accepts a tuple as an argument
- Use spec for DSL definitions generative testing 
- Use spec to convert existing tests to generative and ensure new tests are better quality
- Schema support via spec - initially we should use the schema to hydrate objects according to the spec
- Support for multi-arity tuples, probably passed in as arg in our version of `defsession`
- Figure out a way to keep track of actions - any "action" should be written to a collection by default
- Add sequential counter for facts - might allow more advanced truth maintenance

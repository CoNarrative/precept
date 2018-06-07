## Different usage means different data modeling
Clara (by default) operates over records or maps.
This makes a lot of sense in ordinary back-end business contexts in which the goal is to "reason" over a large body of facts and come quickly to a "decision".
That can be all "additive": there is no need to edit the initial "ground facts". All you need to do is add additional downstream consequences in a logical chain until evaluation is complete.
There need not need any notion of a particular entity changing over the course of the reasoning pass.

Dealing with a persistent session that models ever-progressing mutable app state is another matter.
Here, we want to know *what exactly* changed in the smallest increments possible. 
This is for two reasons, one minor and one deeper:

Minor: We want to avoid loops that are often triggered by "modify" operations of templates/records/facts in rules. Given a map representing an entity with 10 attributes, one of which changes, under Clara *all* rules touch *any* of the other 8 attributes of that entity will also re-fire.
Older rules frameworks like Drools solve this by making objects "property-reactive" so that could technically be addressed in the future at the engine level.

Deep: we want to be able to track *what exactly* changed or is being changed. Even if a true modify with "property-reactive" were implemented, it would still not let us know that an entities `title` attribute just changed as opposed to the `done` attribute.

## EAV

It is the "change of ground facts over time" consideration that we chose to go with an entity-attribute-value tuple way of modeling data.
This not only gives us the right level of change granularity, it's now also a familiar way of modeling data within Clojure thanks to Datomic & Datascript.
We could have also gone with n-tuples which can offer even more concision in some cases, but e-a-v is more pragmatic due to the weight of Datomic/Datascript. 


## Performance Implications
We're not entirely sure how much destructuring what Clara natively thinks as a "map-based" entity into multiple independent e-a-v parts affects Clara's performance.
Since Clara performs joins in parallel, it certainly is not taking advantage of the "exit early" conditional testing that probably takes place when conditions are tested against a single entity.
And there are certainly more joins to track. But so far we haven't hit any real-world performance implications; our attitude is to solve the "developer experience" issue by making sure the data & code shape is the best fit fo this new style of UI coding, and worry about performance limits when and if it becomes an issue.
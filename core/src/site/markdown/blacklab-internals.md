# BlackLab internals

Here we want to document how BlackLab works internally. At the moment it is still very much incomplete, but we intend to add and update information as time goes on.

## Search cache

BlackLab features a cache system that can keep track of results of finished queries as well as currently running queries.

The idea is that uses will often query for something that can benefit from having recent query results available. For example: paging back and forth through a result set; changing the sort for a result set; grouping a result set by some criterium.

The potential disadvantage of such a cache is that it can take up a lot of memory, starving other queries of memory.

The implementation of the cache is left up to the user of BlackLab. BlackLab Server, the webservice accompanying BlackLab, provides an implementation that can be configured by (estimated) size, how long results are kept, what amount of free Java heap memory to strive for, etc. See the configuration of BlackLab Server for details.

These are the important interfaces and classes involved in BlackLab's cache system and BLS's implementation:
- `SearchCache` is the interface that cache implementations must adhere to. Its main two methods are `get()` and `getAsync()`, which take a search (subclass of `Search<R>`, where `R` is the type of `SearchResult` we expect from the search). `get()` will block until the result is available (either found in the cache or executed). `getAsync()` will not block but return a `Future<R>`, that will eventually yield the desired results.
- `Search<R>` and its subclasses represent searches (e.g. a query for hits, with optional sorting, grouping, etc.). It has `execute()` and `executeAsync()` methods that execute the search.
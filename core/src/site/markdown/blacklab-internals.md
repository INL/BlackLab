# BlackLab internals

Here we want to document how BlackLab works internally. At the moment it is still very much incomplete, but we intend to add and update information as time goes on.

## Search cache

BlackLab features a cache system that can keep track of results of finished queries as well as currently running queries.

The idea is that users will often search for something that can benefit from having recent query results available. For example: paging back and forth through a result set; changing the sort for a result set; grouping a result set by some criterium.

The potential disadvantage of such a cache is that it can take up a lot of memory, starving other queries of memory.

The implementation of the cache is left up to the user of BlackLab. BlackLab Server, the webservice accompanying BlackLab, provides an implementation that can be configured in various ways (see `BlsCache` below).

These are the important interfaces and classes involved in BlackLab's cache system and BLS's implementation:
- `Search<R>` and its subclasses represent searches (e.g. a query for hits, with optional sorting, grouping, etc.). It has `execute()` and `executeAsync()` methods that execute the search.
- `BlackLabEngine` is the class that manages the BlackLab search threads. It has a `searchExecutorService` that search threads can be submitted to. (NOTE: we currently use a standard `ForkJoinExecutorService`, but should probably implement our own that ensures that no deadlocks occur between related search tasks)
- `SearchCache` is the interface that cache implementations must adhere to. Its main two methods are `get()` and `getAsync()`, which take a search (subclass of `Search<R>`, where `R` is the type of `SearchResult` we expect from the search). `get()` will block until the result is available (either found in the cache or executed). `getAsync()` will not block but return a `Future<R>`, that will eventually yield the desired results. The cache itself stores these `Future`s, so you can retrieve the "results" of an ongoing search.
- `BlsCache` implements `SearchCache` that manages the cache based on (estimated) size, results "age", amount of free Java heap memory to strive for, etc. See the configuration of BlackLab Server for details.
- `BlsCacheEntry<T extends SearchResult>` represents an entry in the cache. It implements `Future<T extends SearchResult>`. It is returned from `BlsCache.getAsync()`.
- `BlsCacheEntry.SearchTask` is the `Runnable` that is actually started in `BlackLabEngine`'s `searchExecutorService`.
  (NOTE: if we want to prevent deadlocks between related search tasks, this class should probably keep track of other tasks that need its results.

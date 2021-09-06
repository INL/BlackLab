# BlackLab internals

Here we want to document how BlackLab works internally. At the moment it is still very much incomplete, but we intend to add and update information as time goes on.

## Engine, index

- `BlackLabEngine` is the class that manages the BlackLab search threads. It has a `searchExecutorService` that search threads can be submitted to. The `open` and `openForWriting` methods can be used to open indexes.
- `BlackLabIndex` represents a single opened index. You can use it to search the index directly using methods like `find(BLSpanQuery)`, or you can construct a `Search` description using the `search()` methods and then either execute it synchronously using `Search.execute()` or go through the cache using `Search.executeAsync()`.

## Search results

- `SearchResult` is the base interface that all types of results objects implement: `Hits` (and its subclasses like `HitsFromQueryParallel`), `HitGroups`, `DocResults`, `Facets`, etc.

## Search cache

BlackLab features a cache system that can keep track of results of finished queries as well as currently running queries.

The idea is that users will often search for something that can benefit from having recent query results available. For example: paging back and forth through a result set; changing the sort for a result set; grouping a result set by some criterium.

The potential disadvantage of such a cache is that it can take up a lot of memory, starving other queries of memory.

The implementation of the cache is left up to the user of BlackLab. BlackLab Server, the webservice accompanying BlackLab, provides an implementation that can be configured in various ways (see `BlsCache` below).

### BlackLab caching

These are the important interfaces and classes involved in BlackLab's cache system and BLS's implementation:

- `Search<R extends SearchResult>` and its subclasses can be used to build complete descriptions of search requests (e.g. a query for hits, with optional sorting, grouping, etc.). It has `execute()` and `executeAsync()` methods that execute the search task described by the "tree" of `Search`es. The different classes rely on each other's functionality, so `SearchHitsSorted` gets a `SearchHits` as a parameter and will call its `execute` method before sorting the hits produced by that. `Search<R>.executeAsync()` calls `SearchCache.getAsync(this)`, which will return a `Future<R extends SearchResult>` that either has already completed (i.e. the results are actually in the cache from before) or will be executed and produce its results when the future completes (i.e. wasn't in the cache but is now and is being or will be executed).
- `SearchCache` is the interface that cache implementations must adhere to. Its main two methods are `get()` and `getAsync()`, which take a search (subclass of `Search<R extends SearchResult>`, where `R` is the type of `SearchResult` we expect from the search). `get()` will block until the result is available (either found in the cache or executed). `getAsync()` will not block but return a `Future<R>`, that will eventually yield the desired results. The cache itself stores these `Future`s, so you can retrieve the "results" of an ongoing search.

NOTE: `BlackLabEngine`'s `searchExecutorService` currently uses a standard `ForkJoinExecutorService` with `Runnables`, but we should probably either (a) implement our own that ensures that no deadlocks occur between related search tasks or (b) actually use `ForkJoinTask`s and `fork()`/`join()` so we get the advantages of the fork-join model, i.e. tasks waiting for other tasks don't occupy a thread)

NOTE: unless a custom `SearchCache` is configured (like BlackLab Server does), BlackLab uses a dummy cache that doesn't actually cache anything. So `executeAsync()` will block and return an already-completed `Future`, effectively doing the same as `execute()`.

### BLS cache implementation

BlackLab Server implements a custom cache to improve performance for users who might be paging through results, re-ordering results, going from hits to grouped hits, etc.

This works as follows:

- `BlsCache` implements `SearchCache` that manages the cache based on (estimated) memory usage, amount of free Java heap memory to strive for, how long the results have been in the cache, etc. See the configuration of BlackLab Server for details.
- `BlsCacheEntry<T extends SearchResult>` represents an entry in the cache. As `BlsCache` requires, it implements `Future<T extends SearchResult>`. It is returned from `BlsCache.getAsync()`.
- `BlsCacheEntry` submits a `Runnable` to `BlackLabEngine`'s `searchExecutorService`.
  (NOTE: if we want to prevent deadlocks between related search tasks, maybe we should subclass `Runnable` to keep track of other tasks that need its results? If any other running (not queued) tasks need its results, it should not be queued. Maybe do this at the point where the other task waits for the result of this task? Before doing that, the other task marks this task as 'must run' and the executor then lets it run no matter what (NOTE: even if it was queued before, it must now be run!). Maybe `SpansReader`s could always be flagged as 'must run', because they're always created by a running hits task)
- `SpansReader` is also a `Runnable` that is submitted to `BlackLabEngine`'s `searchExecutorService`. (this should probably be given the same treatment as the `Runnable` created in `BlsCacheEntry`)

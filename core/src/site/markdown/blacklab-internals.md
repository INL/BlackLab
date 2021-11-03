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

- `Search<R extends SearchResult>` and its subclasses can be used to build complete descriptions of search requests (e.g. a query for hits, with optional sorting, grouping, etc.). It has `execute()` and `executeAsync()` methods that execute the search task described by the "tree" of `Search`es. The different classes rely on each other's functionality, so `SearchHitsSorted` gets a `SearchHits` as a parameter and will call its `execute` method before sorting the hits produced by that. `Search<R>.executeAsync()` calls `SearchCache.getAsync(this)`, which will return a `SearchCacheEntry<R extends SearchResult>` that either has already completed (i.e. the results are actually in the cache from before) or will be executed and produce its results when the `SearchCacheEntry` (a `Future`) completes (i.e. wasn't in the cache but is now and is being or will be executed).
- `SearchCache` is the interface that cache implementations must adhere to. Its main two methods are `get()` and `getAsync()`, which take a search (subclass of `Search<R extends SearchResult>`, where `R` is the type of `SearchResult` we expect from the search). `get()` will block until the result is available (either found in the cache or executed). `getAsync()` will not block but return a `SearchCacheEntry<R>`, that will eventually yield the desired results. The cache itself stores these `SearchCacheEntry`s, so you can retrieve the "results" of an ongoing search.

NOTE: `BlackLabEngine`'s `searchExecutorService` uses a standard `Executors.newCachedThreadPool()`. It has no maximum number of threads. Instead we limit the maximum number of `BlsCacheEntry`s that can be running at any one time. New `BlsCacheEntry`s submitted when we're already at the maximum number of running entries will be waiting to start (queued) until they can be executed. Note that only top-level searches (i.e. those requested by the user) can be queued; others will be started automatically right away. For example, if the user requests hits for `cat` grouped by metadata field `author`, that entire request may be queued. But when it is unqueued (i.e. started), both the hits request and the grouping of those hits are allowed to run. This way of queueing was chosen to prevent deadlocks when a required intermediate result is not available because it is queued and cannot be started until the waiting thread has finished.

NOTE: unless a custom `SearchCache` is configured (like BlackLab Server does), BlackLab uses a dummy cache that doesn't actually cache anything. So `executeAsync()` will block and return an already-completed `SearchCacheEntry`, effectively doing the same as `execute()`.

### BLS cache implementation

BlackLab Server implements a custom cache to improve performance for users who might be paging through results, re-ordering results, going from hits to grouped hits, etc.

This works as follows:

- `BlsCache` implements `SearchCache` that manages the cache based on the amount of free Java heap memory to strive for, how long a search has been running, how long since results have been accessed, etc. See the configuration of BlackLab Server for details.
- `BlsCacheEntry<T extends SearchResult>` represents an entry in the cache. As `SearchCache` requires, it implements `SearchCacheEntry<T extends SearchResult>`. It is returned from `BlsCache.getAsync()`.
- `BlsCacheEntry` submits a `Runnable` to `BlackLabEngine`'s `searchExecutorService`.
- `SpansReader` is also a `Runnable` that is submitted to `BlackLabEngine`'s `searchExecutorService`. Note that because `SpansReader` is not a `BlsCacheEntry`, we don't actually keep track of these as running searches for load management. It would be better to do this.


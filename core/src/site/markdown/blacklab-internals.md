# BlackLab internals

Here we want to document how BlackLab works internally. At the moment it is still very much incomplete, but we intend to add and update information as time goes on.


## BlackLab technical overview

The basis of a BlackLab index is the Lucene index. The Lucene files are all found in the main 
index directory. Lucene provides the reverse index part of BlackLab, that is, it can quickly 
locate in what documents and at what positions certain word(s) or constructs appear.

You can identify a BlackLab index by the `version.dat` file in the main index directory. This
file should always contain the text `blacklab||2`.

What BlackLab adds to the Lucene index are a _content store_ and _forward indexes_.

### content store

The content store is the simplest to explain: it stores the original (e.g. XML) documents you
added to BlackLab. The documents can be retrieved from the content store if you want to display
them (e.g. using XSLT). Matches may be highlighted in the document.

It is also possible to retrieve snippets from the original document (XML is carefully kept 
wellformed), and even to generate the keyword-in-context (KWIC) view using the original 
content (normally the KWIC view is generated using the forward index, which is faster but doesn't
preserve the original tag structure, e.g. sentence tags, named entity tags, etc.)

In the future, we may want to make the content store optional, because users often have these
documents available from another source, such as a webservice. The challenge in this case is 
to keep highlighting efficient (BlackLab stores its content such that the position information
it gets from Lucene can be used directly to highlight the document).

The content store is found in the subdirectory `cs_contents` (at least, if your annotated 
field is named `contents`; you could even have multiple annotated fields).
The content store subdirectory has its own `version.dat`, which should contain `fixedblock||1`.

### forward index

Each of your annotations can have a forward index.

The combined forward indexes also contain most of the contents of the documents, but missing are
the tags around an in between the words (bold and italic tags, paragraph and sentence tags, 
header and body tags, metadata tags, etc.).

All annotations get a forward index by default, but you 
can [disable this if you want](how-to-configure-indexing.md#disable-fi).

Each annotation has its own forward index directory. These directories are 
named `fi_contents%word`, `fi_contents%word`, etc. (again, assuming your annotated field is 
`contents`). The `version.dat` file in each forward index directory should contain either
`fi||4` or `fi||5`. (these versions differ only in the collators used for sorting terms) 

For more in-depth information about the layout of the non-Lucene files in a BlackLab index, 
see [File formats for the forward index and content store](file-formats.md).

### index metadata file

Each index has a file called `indexmetadata.yaml`. This file contains information that 
BlackLab needs, such as:

- total number of tokens
- pid field
- analyzers used for metadata fields
- whether or not the full content of a document may be retrieved
- metadata field type (tokenized, untokenized, text, numeric)

It also contains extra information that may be useful for applications using BlackLab, such as the "official" [corpus-frontend](https://gibhub.com/INL/corpus-frontend):

- name and description of the index and its fields
- document format name
- metadata fields containing title, author, date
- version info
- text direction, LTR or RTL
- how missing metadata fields were handled during indexing (`unknownValue`, `unknownCondition`)
- metadata field values, display values
- display order for metadata and annotated fields  
- how metadata fields should logically be grouped

It could be argued that the second group of properties don't really belong in BlackLab and
should perhaps be moved to the application using BlackLab. This might be the direction we take 
in the future. We estimate that beside our own corpus-frontend, not many other applications use
these properties.

A complete, documented example of `indexmetadata.yaml` can be found [here](indexing-with-blacklab.md#edit-index-metadata).


### files needed for indexing

index configuration file (`.blf.yaml`) / DocIndexer

A complete, documented example of an input format configuration file can be 
found [here](how-to-configure-indexing.md#annotated-input-format-configuration-file).

### performance optimizations

...


## Module structure

BlackLab has been divided up into modules that serve specific functions.
This is intended to make the structure and dependencies clearer, to make BlackLab
easier to understand and make future improvements easier.

This the current list of modules:

| Module          | Description                                                  |
|-----------------|--------------------------------------------------------------|
| `common`          | classes used by a number of other modules. Currently only contains BlackLab-specific `Exception` subclasses.
| `content-store`   | responsible for storing the input documents indexed in BlackLab for later display with optional highlighting of hits. |
| `contrib/*`       | some modules that serve specific functions that some projects may need, but many don't. Currently contains plugins to convert and tag input documents before indexing, as well as some legacy `DocIndexer` implementations. |
| `core`            | will build the main BlackLab Java library. Doesn't contain any Java code itself but combines other modules (the main module is engine). |
| `engine`          | implements most of the BlackLab functionality. |
| `instrumentation` | two experimental modules for monitoring BlackLab Server using Prometheus or similar. |
| `mocks`           | mock objects useful for testing. Shouldn't be included in library build. |
| `query-parser`    | the main Corpus Query Language parser (as well the more limited Contextual Query Language parser). |
| `server`          | the BlackLab Server web service |
| `text-pattern`    | the `TextPattern` classes that currently sit between the query parser and the `SpanQuery` classes. |

Future plans for this module structure:

- `common` should probably not grow; rather shrink and ideally be eliminated altogether
- `content-store` should be made optional, so you can also use e.g. an external webservice to retrieve the document contents.
- `engine` currently does a lot, with a lot of interdependencies between classes, and could/should therefore be divided up into logical modules.
- `query-parser` could be reduced to just the Corpus Query Language parser, with the Contextual Query Language parser moved into a `contrib` module.
- `text-pattern` could eventually become unnecessary as we move their functionality into the various `SpanQuery` classes, and could then be moved to `contrib` for legacy uses.

## Important classes and their function

### Engine, index

- `BlackLabEngine` is the class that manages the BlackLab search threads. It has a `searchExecutorService` that search threads can be submitted to. The `open` and `openForWriting` methods can be used to open indexes.
- `BlackLabIndex` represents a single opened index. You can use it to search the index directly using methods like `find(BLSpanQuery)`, or you can construct a `Search` description using the `search()` methods and then either execute it synchronously using `Search.execute()` or go through the cache using `Search.executeAsync()`.

### Search results

- `SearchResult` is the base interface that all types of results objects implement: `Hits` (and its subclasses like `HitsFromQueryParallel`), `HitGroups`, `DocResults`, `Facets`, etc.

### Search cache

BlackLab features a cache system that can keep track of results of finished queries as well as currently running queries.

The idea is that users will often search for something that can benefit from having recent query results available. For example: paging back and forth through a result set; changing the sort for a result set; grouping a result set by some criterium.

The potential disadvantage of such a cache is that it can take up a lot of memory, starving other queries of memory.

The implementation of the cache is left up to the user of BlackLab. BlackLab Server, the webservice accompanying BlackLab, provides an implementation that can be configured in various ways (see `BlsCache` below).

#### BlackLab caching

These are the important interfaces and classes involved in BlackLab's cache system and BLS's implementation:

- `Search<R extends SearchResult>` and its subclasses can be used to build complete descriptions of search requests (e.g. a query for hits, with optional sorting, grouping, etc.). It has `execute()` and `executeAsync()` methods that execute the search task described by the "tree" of `Search`es. The different classes rely on each other's functionality, so `SearchHitsSorted` gets a `SearchHits` as a parameter and will call its `execute` method before sorting the hits produced by that. `Search<R>.executeAsync()` calls `SearchCache.getAsync(this)`, which will return a `SearchCacheEntry<R extends SearchResult>` that either has already completed (i.e. the results are actually in the cache from before) or will be executed and produce its results when the `SearchCacheEntry` (a `Future`) completes (i.e. wasn't in the cache but is now and is being or will be executed).
- `SearchCache` is the interface that cache implementations must adhere to. Its main two methods are `get()` and `getAsync()`, which take a search (subclass of `Search<R extends SearchResult>`, where `R` is the type of `SearchResult` we expect from the search). `get()` will block until the result is available (either found in the cache or executed). `getAsync()` will not block but return a `SearchCacheEntry<R>`, that will eventually yield the desired results. The cache itself stores these `SearchCacheEntry`s, so you can retrieve the "results" of an ongoing search.

NOTE: `BlackLabEngine`'s `searchExecutorService` uses a standard `Executors.newCachedThreadPool()`. It has no maximum number of threads. Instead we limit the maximum number of `BlsCacheEntry`s that can be running at any one time. New `BlsCacheEntry`s submitted when we're already at the maximum number of running entries will be waiting to start (queued) until they can be executed. Note that only top-level searches (i.e. those requested by the user) can be queued; others will be started automatically right away. For example, if the user requests hits for `cat` grouped by metadata field `author`, that entire request may be queued. But when it is unqueued (i.e. started), both the hits request and the grouping of those hits are allowed to run. This way of queueing was chosen to prevent deadlocks when a required intermediate result is not available because it is queued and cannot be started until the waiting thread has finished.

NOTE: unless a custom `SearchCache` is configured (like BlackLab Server does), BlackLab uses a dummy cache that doesn't actually cache anything. So `executeAsync()` will block and return an already-completed `SearchCacheEntry`, effectively doing the same as `execute()`.

#### BLS cache implementation

BlackLab Server implements a custom cache to improve performance for users who might be paging through results, re-ordering results, going from hits to grouped hits, etc.

This works as follows:

- `BlsCache` implements `SearchCache` that manages the cache based on the amount of free Java heap memory to strive for, how long a search has been running, how long since results have been accessed, etc. See the configuration of BlackLab Server for details.
- `BlsCacheEntry<T extends SearchResult>` represents an entry in the cache. As `SearchCache` requires, it implements `SearchCacheEntry<T extends SearchResult>`. It is returned from `BlsCache.getAsync()`.
- `BlsCacheEntry` submits a `Runnable` to `BlackLabEngine`'s `searchExecutorService`.
- `SpansReader` is also a `Runnable` that is submitted to `BlackLabEngine`'s `searchExecutorService`. Note that because `SpansReader` is not a `BlsCacheEntry`, we don't actually keep track of these as running searches for load management. It would be better to do this.


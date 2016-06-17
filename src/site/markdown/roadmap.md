# Road map

## Plans for BlackLab 2.0

-   Random sampling of large resultsets.
-   Integrate with Solr and/or ElasticSearch, to allow larger corpora through sharding, etc.
-   Improve performance on a single machine by combining reverse-index search with forward-index based search techniques.
-   Add support for tree-like structures to enable e.g. treebanks-like searches.
-   Further simplify indexing and using BlackLab (Server) in your applications; improve documentation. 
-   Make it easier to write bug-free threaded applications (Hits class might have to become immutable)
-   Make the content store optional, and make it easier for applications to provide their own way of accessing the original content (for hits highlighting).
-   Experiment with a new type of forward index that combines word+pos+lemma, taking less memory and improving performance.
-   Throw more specific Exceptions, use checked Exceptions where it makes sense.
-   Move classes to different packages so some methods that should not be public can be made package-private instead and the packages feel more logical.
-   Provide compacting operations for forward index and content store.
-   Improve testability of the library, add more unit tests (i.e. Searcher should be an abstract base class so you can implement a stub version for testing stuff that requires a Searcher)
-   Integrate improvements and suggestions from the community (indexers, parsers, features, …?) ([mail us](mailto:jan.niestadt@inl.nl))

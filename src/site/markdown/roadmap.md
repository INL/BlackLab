# Road map

## Plans for BlackLab 2.0

-   Provide a generic web service for indexing and searching, so BlackLab can easily be used from different languages and building new search applications becomes easier. See [BlackLab Server](https://github.com/INL/BlackLab-server)!
-   Make the content store optional, and make it easier for applications to provide their own way of accessing the original content (for hits highlighting).
-   Make it easier to write bug-free threaded applications (Hits class might have to become immutable)
-   Experiment with a new type of forward index that combines word+pos+lemma, taking less memory and improving performance.
-   Throw more specific Exceptions, use checked Exceptions where it makes sense.
-   Move classes to different packages so some methods that should not be public can be made package-private instead and the packages feel more logical.
-   Improve the updateability of the index: let the content store reuse free space. Provide compacting operations for forward index and content store.
-   Improve testability of the library, add more unit tests (i.e. Searcher should be an abstract base class so you can implement a stub version for testing stuff that requires a Searcher)
-   Experiment with distributed searching, to search multiple corpora with one query or speed up searching/sorting for a single corpus.
-   Better SRU CQL support (right now it's experimental and very much incomplete)
-   Random sampling of large resultsets
-   Integrate improvements and suggestions from the community (indexers, parsers, features, …?) ([mail me](mailto:jan.niestadt@inl.nl))

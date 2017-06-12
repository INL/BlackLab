# Road map

## Plans for BlackLab 2.0
-   Integrate with Solr and/or ElasticSearch.
-   Scale to larger corpora, by adding support for distributed search. 
-   Add support for tree-like structures to enable e.g. treebanks-like searches.
-   Make it even easier to index your data, especially if you have a custom file format.
-   Make it easier to write bug-free threaded BL applications (Hits class might have to become immutable)
-   Make the content store optional, and make it easier for applications to provide their own way of accessing the original content (for hits highlighting).
-   Experiment with a new type of forward index that combines word+pos+lemma, taking less memory and improving performance.
-   Integrate improvements and suggestions from the community (indexers, parsers, features, …?) ([mail us](mailto:jan.niestadt@ivdnt.org))

# BARK 4 - Performance and resource requirements

- **type:** information
- **status:** active

Why BlackLab has limits, and general ways to improve matters.

## Corpus size

Starting from version 3.0, the theoretical limit to the size of a corpus is 2^63 tokens. Of course, there are other reasons why a corpus can grow beyond what is practical to query, such as available memory, CPU cores, disk access speed, etc.; see the next section.

## Performance

There are several factors that influence BlackLab's performance, e.g.:

- corpus size (larger corpora tend to be slower, because there's more results to process)
- number of corpora on a server (querying many corpora tends to be slower than many queries on one corpus, because of how it affects OS caching)
- query "weight" (some queries require much more work to process than others)
- query volume (how many queries are being submitted)

There are different BlackLab performance metrics that may be relevant to you:
- query throughput, i.e. how many queries can be processed in a given time period.
- query latency, i.e. how long it takes to process a query.

If you perform many relatively light queries on many small indexes, throughput is likely what you care most about. If on the other hand you perform a few relatively heavy queries on one huge index, query latency might be more important to you.

Depending on your use case, you probably care about one kind of performance more than the other.

Query throughput could be improved by eliminating small inefficiencies that are done for every query. For example, if for every query submitted, you go through the entire results cache and perform calculations to determine whether each cached result should be dropped or not, that will affect throughput much more than it will affect latency.

Query latency can be improved in many ways, depending on what's the slowest aspect of query execution:
- more Java heap memory can mean less garbage collector overhead
- obviously a faster disk (fast SSD, faster connection to the SAN, etc.) can speed things up, but more memory for OS disk cache can do the same.
- more available CPU cores can improve parallelism
- a more efficient algorithm for resolving a query can save a lot of CPU time (and/or memory, disk access, etc.). This can include a smarter index format that stores information in the way it's likely to be needed. 
- eliminating efficiencies in how BlackLab uses the Lucene index can speed things up. For example, Lucene works per segment, but that's not true for all operations in BlackLab. This can create inefficient access patterns that slow things down. 

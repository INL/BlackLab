# Distributed Search (Proof of Concept)

This branch is intended as a proof of concept for a distributed search webservice. It is not intended to be merged. If succesful, we will integrate BlackLab with Solr(Cloud) to enable distributed indexing and search.


## Curious?

To try out this (VERY experimental!) aggregator:
- Set up 3 BLS instances that have one or more corpora with the same name (but different documents)
- Build and install the aggregator on another server
- Configure the aggregator by creating a file `aggregator.yaml` in either `$HOME/.blacklab/`, `/etc/blacklab/` or `/vol1/etc/blacklab/`:
  ```yaml
  nodes:
    - http://node1.example.com:8080/blacklab-server
    - http://node2.example.com:8080/blacklab-server
    - http://node2.example.com:8080/blacklab-server
  ```


## Goals

We want to be able to quickly answer these questions:
- does implementing basic distributed search present any major issues?
- how does distributed search affect search performance for different operations?

If the answers to these questions are positive, we will go ahead with the SolrCloud integration project.


## Status

- [ ] Aggregator with:
  - [x] `/`
  - [x] `/input-formats` (returns empty list for now)
  - [x] `/INDEX`
  - [x] `/INDEX/hits`
    - [ ] `filter` parameter 
  - [x] `/INDEX/docs/PID`
  - [x] `/INDEX/docs/PID/contents`
- [ ] Optimization
  - [ ] reduce memory requirements using hit index lists
  - [ ] reduce memory requirements using approximate sort index
- [x] Sort options for hits\[grouped\]:
  - [x] no sort
  - [x] sort on context (match/(word) before/(word) after)
  - [x] sort by metadata field
  - [x] sort by group identity
  - [x] sort by group size


## Approach

We will implement a simple **BLS aggregation service** that forwards search requests to several BLS nodes and combines the results. Not all operations will be supported, just enough to test if the basic approach works and get some idea of how this could affect performance.

We will implement at least:
- hits search, with the option filter and sort hits
- group hits by metadata field or context, with the option to sort groups

The service will have the same API as BLS, albeit a subset. This allows us to use it with corpus-frontend.

Not a specific goal, but a side benefit of implementing part of the BLS API in JAX-RS is that we may be able to use this in the future, either to replace the current `DataStream`-based approach and/or to be able to provide backwards compatibility if we decide to [evolve the API](api-redesign/README.md) to a incompatible version.
.

The service will communicate using JSON for now; if this is found to be a major bottleneck, we may 
investigate adding a binary protocol to `DataStream`.

The service will do very simple caching of (partial) result sets for a short time, e.g. 5 minutes.

We will test the service for correctness and performance.


## Questions

- How big a corpus do we need to effectively test performance?
  
- Does using our central SAN present a bottleneck? If so, could local SSDs solve this?
- Is it enough to distribute a corpus over multiple (virtual) disk volumes, or do we need separate servers? 
- How many nodes should the cluster have?


## Test hardware (chn-intern corpus)

## single-machine specs

CHN-intern current corpus size: 280G (2.2 billion tokens)

- svotmc10: 16 cores, 48G memory   
- svatmc10: 12 cores, 48G memory
- svowmc01: 8 cores, 64G memory

## cluster specs

Start with 3 nodes, each with around 750M tokens.

For each node:
- 150G disk space
- 8 cores    (not a bottleneck with single-user testing)
- 24G memory (16G turned out to be too low to have both enough JVM heap and significant OS disk cache)

(total 24 cores, 72G memory)

(aggregator can run on one of the nodes or on svotmc10)


## Implementation details

### Merging hits results

The aggregator has to merge (potentially large) result sets from each node in the cluster.

If the client only requested a single page of results, we don't need all results from each node.

If we assume a sort has been specified, to be certain we can produce the first N hits we would need the first N hits from each node. We could start merging these results until we have N hits in the correct order and return them. We would have to cache the merge-in-progress.

Producing M hits starting from hit number N is not as easy. In theory, you would need the first N+M hits from each node, as the first hit from a node might be part of the requested hits.

In practice, just fetching pages from the nodes as needed and merging until enough hits-in-order are available to answer the client's request is probably a good approach; users will rarely skip ahead to the 100th page right away.

It also mirrors how Lucene already works: you can't skip hits, you have to iterate over all of them.

If no sort has been specified, it probably can't be guaranteed that hits will be in any specific order (not even increasing document ids like Lucene normally produces, because of parallel fetching from different index segments), merging should probably be done so all hits in 1 document stay together at least. Then we could round-robin around the nodes, taking the hits from one document from each node.

**STATUS:** IMPLEMENTED.

### Merging grouping results

With sorted grouping results, we could do something similar to the above, fetching a page of groups from each node at a time and merging them together until enough groups are available to answer the client's request.

With unsorted grouping results, you would need the full grouping per node. But grouping results always tend to be sorted by either size or group identity.

For this proof of concept, it might be enough to always get the full grouping per node and merge that. As long as we limit ourselves to groupings that have no more than a few hundred groups, that shouldn't cause big performance issues.

**STATUS:** IMPLEMENTED (non-paging version)

### Avoid caching all results using a merge table

Storing a large result set on the aggregator requires a lot of memory.

However, not storing anything can make paging through a result set very slow, especially if the user navigates to the end of a large result set.

Do we always need to keep all the hits we've read, or could we consider maintaining some sort of bookkeeping of the merge process so we can quickly reconstruct the correct hits starting from a certain index?

Imagine we keep a 'merge table' like this:

| hit index | node 1 index | node 2 index | node 3 index |
|-----------|--------------|--------------|--------------|
| 0         | 0            | 0            | 0            |
| 1000      | 300          | 200          | 500          |
| 2000      | 700          | 500          | 800          |
| 3000      | 1300         | 600          | 1100         |

Now if the client requests hits 1300-1400, we can start from hit index 1000, start querying each node at the correct index, and construct the partial list of hits up to 1400, after which we can return the requested window.

With a table like this, we could selectively drop part of the results (for example, anything far away from where the user is currently browsing), or even consider not storing any hits at all.

If no sort was requested, we have to make sure our merge algorithm for this case works with this merge table. We use the node index to choose which node to take hits from, which is not a problem, but we also make sure that hits in the same document stay together, which is tricky if the node index in the table is in the middle of a list of hits from the same document. For this reason, we probably need to store the "document id of previous hit" (so document id for hit index 999 for the table entry starting with index 1000), so we can still apply the same algorithm and get a stable result.

NOTE: for all of the above, but especially for the no-sort case, this trick relies on node responses being stable, i.e. the same request parameters always yielding the same hits in the same order. The "same hits" part is true, but the "same order" is not guaranteed (it is as long as the results are in the cache, but not when the query is run again). With the "no sort" option the ordering depends on how the hits are produced from Lucene segments by several parallel threads. Each segment should produce a stable hits order (increasing document id), but currently these hits are collected in a global structure as they come in. To ensure a stable ordering, we would need to use a merging algorithm (i.e. one similar to how the aggregator merges unsorted results from the nodes). Even when a sort was specified, not all sort criteria are "complete": sorting hits by a document metadata field does not guarantee what order the individual hits from a document will come in. Also, sorting on context before match may place several hits at the start of a document as the first hits in the result set, without a defined ordering between them.

### Less data per hit / approximate sort

Storing full context (before/match/after) for each hit in string form will require a lot of memory for large result sets, as well as a lot of network traffic for information that we don't always need. Also, sorting/grouping would rely on string comparisons instead of the quick sort order index comparison we use now.

Do we need all the context data? We need this data for the concordances in the window requested by the client, but that's usually small, and we could request the context for these hits only when needed, instead of keeping them in memory for all hits. We do need all of the context for certain sort/group operations; but could we save memory and network bandwidth if we adopt a different approach for these operations?

A **global term sort index table** would be ideal, as we wouldn't have to transmit and store strings. But this is challenging to do in a distributed environment, especially in combination with incremental indexing. For example, if a document is added to a node, and it contains a previously unseen term (that doesn't yet have a sort index), it would have to be assigned the correct sort index in the table (potentially updating other terms' sort indexes to avoid collisions), and these changes would have to be communicated to all nodes before any queries including this term could be done. But what if those nodes have (potentially conflicting) changes of their own? And what if one or more nodes were temporarily unavailable during this synchronization process? When those nodes become available again, changes between the term sort index tables would need to be resolved. The term sort index table would keep growing even if a lot of documents are removed unless we keep careful track of what terms still actually occur in the index.

It might be easier to keep an "approximate sort index" (ASI) for each hit, say an integer, with the following property: if two hits have different ASIs, their correct sort order follows from that. If two hits have the same ASI, it means they will sort close together, but we're not sure which comes first. This way we can keep "approximately sorted" hits in memory, and request additional data to refine the sort only for the requested window (or potentially slightly larger depending on how hits with the same ASI are clustered).

The same ASI approach could work for all type of sort, not just the context ones. This means we don't need to store all metadata fields for all documents in the result set to enable sorting by a metadata field.

An approximate sort index for a HitProperty of a hit could be created by (1) determining a string version of its "most significant part" (e.g. first few characters, or first property if multiple properties specified), (2) determining the CollationKey for that part and (3) getting the corresponding byte array, taking the first (most significant) 4-8 bytes and converting them to a numeric value.

In cases where there's a lot of ASI collisions (e.g. many hits in the same document when sorting by document pid) that will make sorting slower, but shouldn't break anything.

If we remember each hit's originating node and its index in that node's result set, we could use that to sort hits from the same node with identical ASIs. This works because each node produces hits sorted using that node's sort index table. This could save a number of string comparisons with the final sort, at the cost of a bit of additional memory.

For grouping, the same technique could be applied, although there's probably less of a problem, because we usually don't have millions of groups.

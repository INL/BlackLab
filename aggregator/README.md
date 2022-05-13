# Distributed Search (Proof of Concept)

This branch is intended as a proof of concept for a distributed search 
webservice. It is not intended to be merged. If succesful, we will integrate 
BlackLab with Solr(Cloud) to enable distributed indexing and search.


## Goals

We want to be able to quickly answer these questions:
- does implementing basic distributed search present any major issues?
- how does distributed search affect search performance for different 
 operations?

If the answers to these questions are positive, we will go ahead with the 
SolrCloud integration project.


## Status

- [ ] Aggregator with:
  - [x] `/`
  - [x] `/input-formats` (returns empty list for now)
  - [x] `/INDEX`
  - [x] `/INDEX/hits`
  - [x] `/INDEX/docs/PID`
  - [x] `/INDEX/docs/PID/contents`
  - [ ] MORE?
- [ ] Sort options for hits\[grouped\]:
  - [x] use collator for sort
  - [x] sort on context (match/(word) before/(word) after)
  - [x] sort by metadata field
  - [x] sort by group identity
  - [x] sort by group size
  - [ ] MORE?

## Approach

We will implement a simple **BLS aggregation service** that forwards search 
requests to several BLS nodes and combines the results. Not all operations will 
be supported, just enough to test if the basic approach works and get some idea 
of how this could affect performance.

We will implement at least:
- hits search, with the option filter and sort hits
- group hits by metadata field or context, with the option to sort groups

The service will have the same API as BLS, albeit a subset. This allows us to 
use it with corpus-frontend.

Not a specific goal, but a side benefit of implementing part of the BLS API in
JAX-RS is that we may be able to use this in the future, either to replace the 
current `DataStream`-based approach and/or to be able to provide backwards 
compatibility if we decide to [evolve the API](BLS-API-REDESIGN.md) to a incompatible version.
.

The service will communicate using JSON for now; if this is found to be a major 
bottleneck, we may 
investigate adding a binary protocol to `DataStream`.

The service will do very simple caching of (partial) result sets for a short 
time, e.g. 5 minutes.

We will test the service for correctness and performance.


## Open questions

- How big a corpus do we need to effectively test performance?
- Does using our central SAN present a bottleneck? If so, could local SSDs 
  solve this?
- Is it enough to distribute a corpus over multiple (virtual) disk volumes, or 
  do we need separate servers?
- How many nodes should the cluster have?


## Test hardware (chn-intern corpus)

## single-machine specs

CHN-intern current corpus size: 280G

- svotmc10: 16 cores, 48G memory   
- svatmc10: 12 cores, 48G memory
- svowmc01: 8 cores, 64G memory

## cluster specs

Start with 3 nodes.

For each node:
- 150G disk space
- 8 cores    (increase if this is a bottleneck)
- 24G memory (probably not a bottleneck)

(total 24 cores, 72G memory)

(aggregator can run on one of the nodes or on svotmc10)


## Implementation details

### Merging hits results

The aggregator has to merge (potentially large) result sets from each node in 
the cluster.

If the client only requested a single page of results, we don't need all 
results from each node.

If we assume a sort has been specified, to be certain we can produce the first 
N hits we would need the first N hits from each node. We could start merging 
these results until we have N hits in the correct order and return them. We 
would have to cache the merge-in-progress.

Producing M hits starting from hit number N is not as easy. In theory, you 
would need the first N+M hits from each node, as the first hit from a node 
might be part of the requested hits.

In practice, just fetching pages from the nodes as needed and merging until 
enough hits-in-order are available to answer the client's request is probably a 
good approach; users will rarely skip ahead to the 100th page right away.

It also mirrors how Lucene already works: you can't skip hits, you have to 
iterate over all of them.

If no sort has been specified, it probably can't be guaranteed that hits will 
be in any specific order(not even increasing document ids like Lucene normally 
produces, because of `HitsFromQueryParallel`), merging should probably be done 
so all hits in 1 document stay together at least. Then we could round-robin 
around the nodes, taking the hits from one document from each node.

For this proof of concept, we could consider disregarding hits searches without 
sorts.


### Merging grouping results

With sorted grouping results, we could do something similar to the above, 
fetching a page of groups from each node at a time and merging them together 
until enough groups are available to answer the client's request.

With unsorted grouping results, you would need the full grouping per node.

For this proof of concept, it might be enough to always get the full grouping 
per node and merge that. As long as we limit ourselves to groupings that have 
no more than a few hundred groups, that shouldn't cause big performance issues.

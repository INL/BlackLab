# Plans: Solr integration

This is where we will keep track of our current development tasks.

The current major task is to enable BlackLab to integrate with Solr. The goal is to utilize Solr's distributed indexing and search capabilities with BlackLab.

Integrating with Solr will involve the following steps:

These tasks will not necessarily be discretely executed in this order, but some tasks might overlap (e.g. we might tackle group hits standalone, then distributed, then do the other operations).

## Useful independent tasks:

- [ ] Make content store optional and investigate alternatives (e.g. content webservice)<br>(OPTIONAL BUT RECOMMENDED - Lucene is not intended for storing large documents and keeping the content on the same server could thrash the disk cache. A webservice on another host is probably be better. Only downside is that making concordances from the original content instead of the forward index won't be very feasible anymore, so an integrated content store as an option is still nice and not that hard to do.)
- [ ] Perform more hits operations per index segment instead of "globally"<br> Filtering, sorting and grouping could be done per segment instead of how it is done now. Of course a merge step would be needed to combine sorted/grouped results from each segment, just as with distributed search.<br>
  (OPTIONAL BUT RECOMMENDED - Because the forward index is now part of a segment, it makes sense to try to do everything related to this segment before merging segment results, as this minimizes resource contention, makes disk reads less disjointed, and is more efficient in general (because it stays closer to Lucene's design))
- [ ] [Other open issues](https://github.com/INL/BlackLab/issues)<br>
  (probably prioritize issues that can be solved quickly, bugs, and features we actually need or were requested by users; tackle very complex issues and enhancements that may be of limited use for later)

## Enable trunk-based development

- [x] Make it possible to configure feature flags
- [ ] Make it possible to rerun tests with different flag values (probably by re-running all tests with different flags, set via env.vars)
- [ ] Make it easier and more obvious how to configure BlackLab programmatically, instead of requiring config file(s) in specific locations, having settings copied (ensureGlobalConfigApplied), and having config be global instead of per-Engine (although that may not be a huge deal). (improves testability, e.g. change feature flag for certain tests).<br>(OPTIONAL BUT RECOMMENDED)

## Incorporate all information into the Lucene index

- [ ] Make the forward index part of the Lucene index
- [ ] [Compress the forward index?](https://github.com/INL/BlackLab/issues/289), probably using VInt, etc. which Lucene incorporates and Mtas already uses.<br>(OPTIONAL BUT RECOMMENDED)
- [ ] Make indexmetada.yaml part of the Lucene index
- [ ] Make content store part of the Lucene index (using the same compression as we have now, or perhaps a compression mechanism Lucene already provides..? Look in to this)
- [ ] Eliminate index version files (replaced by codec version)

## Integrate with Solr (standalone)

- [ ] Study how Mtas integrates with Solr
- [ ] Add a request handler that can perform a simple BlackLab request (e.g. group hits)
- [ ] Add other operations to the request handler (find hits, docs, snippet, metadata, highlighted doc contents, etc.)
- [ ] Enable indexing via Solr (custom or via standard import mechanisms?)
- [ ] Create a Dockerfile for Solr+BlackLab
- [ ] Make it possible to run the tests on the Solr version too

## Enable Solr distributed

- [ ] Experiment with non-BlackLab distributed Solr, to learn more about e.g. ZooKeeper
- [ ] Enable distributed indexing
- [ ] Make one of the search operations (e.g. group hits) work in distributed mode
- [ ] Make other search operations work in distributed mode
- [ ] Create a Docker setup for distributed Solr+BlackLab
- [ ] Make it possible to run the tests on the distributed Solr version


>>>>>>> Add PLAN.md.

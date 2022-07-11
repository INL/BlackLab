# Plans: Solr integration

This is where we will keep track of our current development tasks.

The current major task is to enable BlackLab to integrate with Solr. The goal is to utilize Solr's distributed indexing and search capabilities with BlackLab.

Integrating with Solr will involve the following steps:

These tasks will not necessarily be discretely executed in this order, but some tasks might overlap (e.g. we might tackle group hits standalone, then distributed, then do the other operations).

## Useful independent tasks:

- [ ] Make content store optional and investigate alternatives (e.g. content webservice)<br>(OPTIONAL BUT RECOMMENDED - There's pros and cons, but it's good to have the option of an external content store. Only downside is that making concordances from the original content instead of the forward index won't be very feasible anymore, so an integrated content store as an option is still nice and not that hard to do.)
- [ ] Perform more hits operations per index segment instead of "globally"<br> Filtering, sorting and grouping could be done per segment instead of how it is done now. Of course a merge step would be needed to combine sorted/grouped results from each segment, just as with distributed search.<br>
  (OPTIONAL BUT RECOMMENDED - Because the forward index is now part of a segment, it makes sense to try to do everything related to this segment before merging segment results, as this minimizes resource contention, makes disk reads less disjointed, and is more efficient in general (because it stays closer to Lucene's design))
- [ ] [Other open issues](https://github.com/INL/BlackLab/issues)<br>
  (probably prioritize issues that can be solved quickly, bugs, and features we actually need or were requested by users; tackle very complex issues and enhancements that may be of limited use for later)

## Enable trunk-based development

- [x] Make it possible to configure feature flags
- [x] Run integration tests with both values of `integrateExternalFiles`
- [ ] Figure out how to effectively run the same unit tests on multiple implementations of the same interface. [Using generics and inheritance?](https://stackoverflow.com/a/16237354)
- [ ] Make it easier and more obvious how to configure BlackLab programmatically, instead of requiring config file(s) in specific locations. (improves testability, e.g. change feature flag for certain tests).<br>(OPTIONAL BUT RECOMMENDED)

## Incorporate all information into the Lucene index

- [x] Eliminate index version files (replaced by codec version)
- [x] Make the forward index part of the Lucene index
  - [ ] Only create forward index for annotations that should have one (fieldsconsumer)
- [ ] Make indexmetada.yaml part of the Lucene index. Probably take the opportunity to refactor and simplify related code as much as possible. E.g. use Jackson, get rid of old settings, don't try to autodetect stuff from the index, etc.
- [ ] Make content store part of the Lucene index (using the same compression as we have now, or perhaps a compression mechanism Lucene already provides..? Look in to this)<br>How do we add the content to the index? Could we create a custom field type for this or something (or otherwise register the field to be a content store field, maybe via a field attribute..?), which we store in such a way that it allows us random access..? Or do we simply obtain a reference to the FieldsConsumer and call a separate method to add the content to the store?

## Refactoring opportunities

- [ ] In general: refactor for looser coupling / improved testability.
- [x] Use more clean interfaces instead of abstract classes for external API.
- [x] BlackLabIndexImpl should be abstract, with separate External and Internal implementations.
- [ ] Combine / eliminate the fiid-related methods in BlackLabIndex as much as possible.
  - [ ] ForwardIndexAccessor(LeafReader) seems to have a lot of similarities with how ForwardIndexIntegrated and SegmentForwardIndex work. Could they use a single interface, with a external (legacy) implementation and internal implementations?
  - [ ] Also look at FiidLookup and DocIntFieldGetter
- [ ] Don't rely on BlackLab.defaultConfigDirs() in multiple places.
      Specifically DocIndexerFactoryConfig: this should use an option from blacklab(-server).yaml, 
      with a sane default. Remove stuff like /vol1/... and /tmp/ from default config dirs.

## Optimization opportunities

The first implementation of the integrated index is slow, because we just want to make it work for now. There are a number of opportunities for optimizing it.

Because this is a completely new index format, we are free to change its layout on disk to be more efficient.

- [ ] Use more efficient data structures in the various `*Integrated` classes, e.g. those from fastutil
- [ ] In addition to the traditional global `ForwardIndex` and `Terms` APIs, also add per-leafreader APIs that can be used to optimize operations on integrated indexes. But do keep non-integrated indexes working as well.
- [x] In `BLFieldsProducer`, we clone `IndexInput` for every method call that needs access to a file. The reason is threadsafety. But too much cloning could be slow, so we could instead create objects that clone the required `IndexInput`s once for a thread and then use those for every method call.
- [ ] `TermsIntegrated` doesn't read the terms files we wrote but gets the turms using `TermsEnum` from Lucene. It seems good not to store the terms twice (once by Lucene and once by us), but this does mean we can't use memory-mapped random access to the term strings. So switching to our own terms file might be necessary.
- [ ] Investigate if there is a more efficient way to read from Lucene's `IndexInput` than calling `readInt()` etc. repeatedly. How does Lucene read larger blocks of data from its files?
- [ ] Interesting (if old) [article](https://blog.thetaphi.de/2012/07/use-lucenes-mmapdirectory-on-64bit.html) about Lucene and memory-mapping. Recommends 1/4 of physical memory should be Java heap, rest for OS cache. Use `iotop` to check how much I/O swapping is occurring.
- [ ] [Compress the forward index?](https://github.com/INL/BlackLab/issues/289), probably using VInt, etc. which Lucene incorporates and Mtas already uses.<br>(OPTIONAL BUT RECOMMENDED)


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

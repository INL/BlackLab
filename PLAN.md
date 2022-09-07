# Plans: Solr integration

This is where we will keep track of our current development tasks.

The current major task is to enable BlackLab to integrate with Solr. The goal is to utilize Solr's distributed indexing and search capabilities with BlackLab.

Integrating with Solr will involve the following steps.

## Useful independent tasks:

- [ ] Perform more hits operations per index segment instead of "globally"<br> Filtering, sorting and grouping could be done per segment instead of how it is done now. Of course a merge step would be needed to combine sorted/grouped results from each segment, just as with distributed search.<br>
  (OPTIONAL BUT RECOMMENDED - Because the forward index is now part of a segment, it makes sense to try to do everything related to this segment before merging segment results, as this minimizes resource contention, makes disk reads less disjointed, and is more efficient in general (because it stays closer to Lucene's design))
- [ ] [Other open issues](https://github.com/INL/BlackLab/issues)<br>
  (probably prioritize issues that can be solved quickly, bugs, and features we actually need or were requested by users; tackle very complex issues and enhancements that may be of limited use for later)

## Improve testing

### Unit tests

- [ ] Run more of the unit test suites on both the classic and the integrated index format. See e.g. TestSearches.
- [ ] Make sure (basic) config-based indexing is unit tested too.
 
### Integration tests

- [ ] Test that file formats remain unchanged, by including small already-indexed corpora for both supported index formats in the repo and test those.


## Incorporate all information into the Lucene index

### Metadata

- [x] Store metadata in "special" document. Preferably, don't treat it as a special document, just a document in the index that doesn't have a value for the contents field.
    - [ ] metadata may change during indexing after all? no more undeclared metadata field warning?
          OTOH, changing metadata as documents are added to the index would be tricky in distributed env...
    - [ ] QueryTool: enter command `filter *:*`, then command `docs`. The index metadata document is not skipped.
          ideally, there should be a guarantee that DocResults cannot include the metadata document.

Where we take the metadata document into account:
- whenever we iterate over all documents to do something (BlackLabIndex.forEachDocument explicitly skips metadata document)
- DocValues (metadata doc just won't have a value for any of the fields)
- SpansNGrams, SpansNot (use lengthgetter which returns 0 if annotated field is not in document)
- HitsFromQuery[Parallel] (only if one of the Spans could potentially produce the metadata document as hit, which they shouldn't)
- anywhere where `liveDocs` is used (checked, see some the above)
- anywhere where `MatchAllDocsQuery` is used (replaced with BlackLabIndex.getAllRealDocsQuery())


### Forward index

- [ ] ForwardIndexAccessorLeafReader: implement per-segment terms class (or as part of ForwardIndexSegmentReader). Use same approach as global terms when comparing insensitively.
- [ ] Speed up index startup for integrated index. Currently slow because it determines global term ids and sort positions by sorting all the terms. Unfortunately, there is no place in the Lucene index for global information, so this is a challenge. Maybe we can store the sort positions per segment and determine global sort positions by merging the per-segment lists efficiently (because we only need to compare strings if we don't know the correct order from one of the segment sort positions lists)<br/>
  Essentially, we build the global terms list by going through each leafreader one by one (as we do now), but we also keep a sorted list of what segments each term occurs in and their sortposition there (should automatically be sorted because we go through leafreaders in-order). Then when we are comparing two terms, we look through the list of segmentnumbers to see if they occur in the same segment. If they do, the segment sort order gives us the global sort order as well.<br/>
 (Ideally, we wouldn't need the global term ids and sort positions at all, but would do everything per segment and merge the per-segment results using term strings.)
- [ ] capture tokens encoding (maybe also rename to "tokens codec"?) in a class as well, like CS.
- [ ] IndexInput.clone() is NOT threadsafe, so we must do this in a synchronized method!


### Content store

- [ ] poke a hole to get direct access to the content store, similar to `BlackLab40PostingsReader.get(lrc).forwardIndex()`.
- [ ] Right now `BlackLab40StoredFieldsReader` is made threadsafe using expensive synchronization of a whole long method, `getValueSubstring()`. It's probably better to clone the file handles and use those in a nonthreadsafe class, similar to `SegmentForwardIndex` / `ForwardIndexSegmentReader`.\
 (**Assuming it needs to be threadsafe!** Or does Lucene guarantee that one segment is only ever read by one thread? For writing that seems to be the case, but that's very different of course)
- [ ] actually compress content store
- [ ] completely finish implementing `BlackLab40StoredFieldsReader/Writer` (see remaining TODOS)


## Refactoring opportunities

- [ ] In general:
  - [ ] refactor for looser coupling / improved testability.
  - [ ] Use more clean interfaces instead of abstract classes for external API.
  - [ ] search for uses of `instanceof`; usually a smell of bad design
- [ ] addToForwardIndex shouldn't be a separate method in DocIndexers and Indexer; there should be an addDocument method that adds the document to all parts of the BlackLab index.
- [ ] Don't rely on BlackLab.defaultConfigDirs() in multiple places.
      Specifically DocIndexerFactoryConfig: this should use an option from blacklab(-server).yaml, 
      with a sane default. Remove stuff like /vol1/... and /tmp/ from default config dirs.

## Optimization opportunities

The first implementation of the integrated index is slow, because we just want to make it work for now. There are a number of opportunities for optimizing it.

Because this is a completely new index format, we are free to change its layout on disk to be more efficient.

- [ ] ForwardIndexDocumentImpl does a lot of work (e.g. filling chunks list with a lot of nulls), but it regularly used to only read 1-2 tokens from a document; is it worth it at all? Could we use a more efficient implementation?
- [ ] Use more efficient data structures in the various `*Integrated` classes, e.g. those from fastutil
- [ ] `TermsIntegrated` doesn't read the terms files we wrote but gets the terms using `TermsEnum` from Lucene. It seems good not to store the terms twice (once by Lucene and once by us), but this does mean we can't use memory-mapped random access to the term strings. So switching to our own terms file might be necessary.
- [ ] Investigate if there is a more efficient way to read from Lucene's `IndexInput` than calling `readInt()` etc. repeatedly. How does Lucene read larger blocks of data from its files?
- [ ] Interesting (if old) [article](https://blog.thetaphi.de/2012/07/use-lucenes-mmapdirectory-on-64bit.html) about Lucene and memory-mapping. Recommends 1/4 of physical memory should be Java heap, rest for OS cache. Use `iotop` to check how much I/O swapping is occurring.
- [ ] [Compress the forward index?](https://github.com/INL/BlackLab/issues/289), probably using VInt, etc. which Lucene incorporates and Mtas already uses.<br>(OPTIONAL BUT RECOMMENDED)


## BlackLab Proxy

The proxy supports the full BlackLab Server API, but forwards requests to be executed by another server:

- Solr (standalone or SolrCloud)
- it could even translate version 2 of the API to version 1 and forward requests to an older BLS. This could help us support old user corpora in AutoSearch.

Tasks:

- [ ] Adapt the aggregator to be a generic BlackLab proxy with pluggable backend
- [ ] Translate BLS requests (API versions 1 and 2) to Solr requests.
- [ ] Translate between BLS API version 1 to 2.
- [ ] (optional) implement logic to decide per-corpus what backend we need to send the request to. I.e. if it's an old index, send it to the old BLS, otherwise send it to Solr. Also implement a merged "list corpora" view.

## Integrate with Solr (standalone)

- [ ] Refactor BlackLab Server to isolate executing the requests from gathering parameters and sending the response. Essentially, each operation would get a request class (containing all required parameters, checked, converted and ready for BlackLab to use) and results class (containing the requested hits window, docinfos, and an object for the running count). We can reuse these classes and the methods that perform the actual operations when we implement them in Solr. They can also form the basis for API v2 in BlackLab Server itself.
- [ ] Study how Mtas integrates with Solr
- [ ] Add a request handler that can perform a simple BlackLab request (e.g. group hits)
- [ ] Add other operations to the request handler (find hits, docs, snippet, metadata, highlighted doc contents, etc.)
- [ ] Enable indexing via Solr (custom or via standard import mechanisms?)
- [ ] Make it possible to run the tests on the Solr version too
- [ ] Create a Dockerfile for Solr+BlackLab

## Enable Solr distributed

- [ ] Experiment with non-BlackLab distributed Solr, to learn more about e.g. ZooKeeper
- [ ] Enable distributed indexing
- [ ] Make one of the search operations (e.g. group hits) work in distributed mode
- [ ] Make other search operations work in distributed mode
- [ ] Create a Docker setup for distributed Solr+BlackLab
- [ ] Make it possible to run the tests on the distributed Solr version



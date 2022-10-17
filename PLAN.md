# Plans: Solr integration

This is where we will keep track of our current development tasks.

The current major task is to enable BlackLab to integrate with Solr. The goal is to utilize Solr's distributed indexing and search capabilities with BlackLab.

Integrating with Solr will involve the following steps.

## Solve existing issues

Probably prioritize [issues](https://github.com/INL/BlackLab/issues) that:

- can be done quickly
- bugs that (are likely to) affect us(ers)
- features we actually need or were requested by users
 
Very complex issues and enhancements that may be of limited use should be tackled later.

## How to phase out the global FI API

We would like to eventually eliminate the global forward index API. This means forward index related tasks (sort/group/filter on context, produce KWICs, NFA matching) should operate per index segment, followed by a merge step.

The merge step would use string comparisons instead of term sort order comparisons, so we don't need to keep track of global term sort orders, which is expensive and difficult to do when dynamically adding/removing documents. Such a merge would work in a similar way as with distributed search. 

Other advantages of this appraoch: makes operations more parallellizable, minimizes resource contention, makes disk reads less disjointed, and stays closer to Lucene's design.

This is how the global forward index is currently used, and what it would take to change these uses, from hardest to easiest:

- Kwics / Contexts (constructor, makeKwicsSingleDocForwardIndex, getContextWordsSingleDocument)<br>
  Sorting, grouping, filtering and making KWICs should be done per segment, followed by a merge step that does not use sort positions but string comparisons.
- HitGroupsTokenFrequencies / CalcTokenFrequencies. Should be converted to work per segment. A bit of work but very doable.
- ForwardIndexAccessor: forward index matching (NFAs). Should be relatively easy because forward index matching happens from Spans classes that are already per-segment.
- IndexMetadataIntegrated: counting the total number of tokens. Doesn't use tokens or terms file, and is easy to do per segment.


## Incorporate all information into the Lucene index

### Metadata

- [ ] metadata may change during indexing after all? no more undeclared metadata field warning? OTOH, changing metadata as documents are added to the index would be tricky in distributed env... You should probably check for metadata document updates semi-regularly, and keep more critical information in field attributes.

### Forward index

- [ ] Speed up index startup for integrated index. Currently slow because it determines global term ids and sort positions by sorting all the terms. Unfortunately, there is no place in the Lucene index for global information, so this is a challenge. Maybe we can store the sort positions per segment and determine global sort positions by merging the per-segment lists efficiently (because we only need to compare strings if we don't know the correct order from one of the segment sort positions lists)<br/>
  Essentially, we build the global terms list by going through each leafreader one by one (as we do now), but we also keep a sorted list of what segments each term occurs in and their sortposition there (should automatically be sorted because we go through leafreaders in-order). Then when we are comparing two terms, we look through the list of segmentnumbers to see if they occur in the same segment. If they do, the segment sort order gives us the global sort order as well.<br/>
 (Ideally, we wouldn't need the global term ids and sort positions at all, but would do everything per segment and merge the per-segment results using term strings.)
- [ ] capture tokens encoding (maybe also rename to "tokens codec"?) in a class as well, like CS. Consider pooling encoder/decoder as well if useful.
- [ ] check the maximum token id in each document. If less than 256, use a single byte per token, two bytes if less than 16384, etc. Store this number of bytes per token as a parameter for the tokens codec.
- [ ] IndexInput.clone() is NOT threadsafe, so we must do this in a synchronized method!
- [ ] can we implement a custom merge here like CS? i.e. copy bytes from old segment files to new segment file instead of re-reversing the reverse index. (briefly looked at this but this doesn't appear to be feasible?)

### Content store

LATER? 
- [ ] ContentStoreSegmentReader getValueSubstrings more efficient impl? This is possible, but maybe not the highest priority.
- [ ] implement custom merge? The problem is that we need to split the `MergeState` we get into two separate ones, one with content store fields (which we must merge) and one with regular stored fields (which must be merged by the delegate), but we cannot instantiate `MergeState`. Probably doable through a hack (placing class in Lucene's package or using reflection), but let's hold off until we're sure this is necessary.


## Refactoring opportunities

- [ ] Tasks:
    - [ ] search for uses of `instanceof`; usually a smell of bad design
          (but allowable for legacy exceptions that will go away eventually)
    - [ ] addToForwardIndex shouldn't be a separate method in DocIndexers and Indexer; there should be an addDocument method that adds the document to all parts of the BlackLab index.
    - [ ] Don't rely on BlackLab.defaultConfigDirs() in multiple places.
      Specifically DocIndexerFactoryConfig: this should use an option from blacklab(-server).yaml,
      with a sane default. Remove stuff like /vol1/... and /tmp/ from default config dirs.
- [ ] Principles:
  - [ ] refactor for looser coupling / improved testability.
  - [ ] Use more clean interfaces instead of abstract classes for external API.

## Optimization opportunities

The first implementation of the integrated index is slow, because we just want to make it work for now. There are a number of opportunities for optimizing it.

Because this is a completely new index format, we are free to change its layout on disk to be more efficient.

- [ ] ForwardIndexDocumentImpl does a lot of work (e.g. filling chunks list with a lot of nulls), but it regularly used to only read 1-2 tokens from a document; is it worth it at all? Could we use a more efficient implementation?
- [ ] Use more efficient data structures in the various `*Integrated` classes, e.g. those from fastutil
- [ ] Investigate if there is a more efficient way to read from Lucene's `IndexInput` than calling `readInt()` etc. repeatedly. How does Lucene read larger blocks of data from its files? (you can read/write blocks of bytes, but then you're responsible for endianness-issues)
- [ ] Interesting (if old) [article](https://blog.thetaphi.de/2012/07/use-lucenes-mmapdirectory-on-64bit.html) about Lucene and memory-mapping. Recommends 1/4 of physical memory should be Java heap, rest for OS cache. Use `iotop` to check how much I/O swapping is occurring.
- [ ] [Compress the forward index?](https://github.com/INL/BlackLab/issues/289), probably using VInt, etc. which Lucene incorporates and Mtas already uses.<br>(OPTIONAL BUT RECOMMENDED)


## Integrate with Solr (standalone)

- [x] Refactor BlackLab Server to isolate executing the requests from gathering parameters and sending the response. Essentially, each operation would get a request class (containing all required parameters, checked, converted and ready for BlackLab to use) and results class (containing the requested hits window, docinfos, and an object for the running count). We can reuse these classes and the methods that perform the actual operations when we implement them in Solr. They can also form the basis for API v2 in BlackLab Server itself.
- [ ] Study how Mtas integrates with Solr
- [ ] Add a request handler that can perform a simple BlackLab request (e.g. group hits)
- [ ] Add other operations to the request handler (find hits, docs, snippet, metadata, highlighted doc contents, etc.)
- [ ] Enable indexing via Solr (custom or via standard import mechanisms?)
- [ ] Make it possible to run the tests on the Solr version too
- [ ] Create a Dockerfile for Solr+BlackLab


## BlackLab Proxy

The proxy supports the full BlackLab Server API, but forwards requests to be executed by another server:

- Solr (standalone or SolrCloud)
- it could even translate version 2 of the API to version 1 and forward requests to an older BLS. This could help us support old user corpora in AutoSearch.

Tasks:

- [ ] Adapt the aggregator to be a generic BlackLab proxy with pluggable backend
- [ ] Translate BLS requests (API versions 1 and 2) to Solr requests.
- [ ] Translate between BLS API version 1 to 2.
- [ ] (optional) implement logic to decide per-corpus what backend we need to send the request to. I.e. if it's an old index, send it to the old BLS, otherwise send it to Solr. Also implement a merged "list corpora" view.


## Enable Solr distributed

- [ ] Experiment with non-BlackLab distributed Solr, to learn more about e.g. ZooKeeper
- [ ] Enable distributed indexing
- [ ] Make one of the search operations (e.g. group hits) work in distributed mode
- [ ] Make other search operations work in distributed mode
- [ ] Create a Docker setup for distributed Solr+BlackLab
- [ ] Make it possible to run the tests on the distributed Solr version

# Change Log

## Improvements in 2.2.0-SNAPSHOT

### Changed

* Make forward-index matching much less frequent, as it's difficult to predict when it will help, and it often hurts.
* Split into several projects that each handle specific functionality. More to do here.
* If either `wordsaroundhit` or `parameters.contextSize` are set to 0, don't include left and right context in the 
  results at all.  

### New

* Greatly improve startup time and passive memory usage.
* Improved time to first result in (very) large indices.
* Improved performance by instantiating fewer objects (e.g. using `IntArrayLists` instead of `Hit` objects)
* Greatly improved speed of grouping on "any token" query, e.g. generating a frequency list for all or part of the corpus.
* Searches will now be queued (not started yet) if server load is too high.
  Pausing already-started searches was removed, as this can keep a lot of memory from being used for running searches.
* If a search is aborted for taking too long, it will be held in cache for a while to prevent clients from immediately resubmitting.
* `/search-test/` debug interface (experimental).
* Improved experimental Dockerfile.
* BlackLab Server test suite using Docker, Mocha and Chai.
* Simplified code for better maintainability.
* Documented blacklab internals (see https://inl.github.io/BlackLab/blacklab-internals.html)
* New setting `debug.alwaysAllowDebugInfo`, so each BLS request is considered in debugMode, e.g. /cache-info works, etc.
* New setting `indexing.maxNumberOfIndicesPerUser` to configure how many private indices each user is allowed.
* New setting `parameters.writeHitsAndDocsInGroupedHits` and query parameter includegroupcontents to include hits
  with the grouped results.
* Handle index paths that are relative symlinks.

### Fixed

* Upgrade to `log4j-2.16.0` (fixes [CVE-2021-44228](https://nvd.nist.gov/vuln/detail/CVE-2021-44228) and [CVE-2021-45046](https://nvd.nist.gov/vuln/detail/CVE-2021-45046)).
* Bug in `TermsWriter` that caused a crash when indexing more than 134M unique terms.
* Deadlock because of fixed thread count and some subtasks getting run but not others. Now either allows entire search operation or queues it until later.
* Fix not all hits always counted when grouping/sorting.
* Fix metadata queries on numeric fields not working.
* `HitsFromQueryParallel` queries can now be correctly aborted if they take too long.
* `HitsFromQueryParallel` should work correctly with capture groups now. Sampled hits as well.
* Prevent NPE when not all hits have captured groups.
* Prevent `ConcurrentModificationException` caused by not synchronizing.
* Fix some caching bugs by consistently defining `hashCode()` and `equals()`.
* Report correct search time (used to underreport).
* Many smaller bugfixes.

### Removed

* Support for pre-2.0 configuration files. See https://inl.github.io/BlackLab/configuration-files.html
* Support for `useOldElementnames` (old BLS element names, using "properties" instead of "annotations")


## Improvements in 2.1.1

### Fixed

* Upgrade to log4j-2.16.0 (fixes CVE-2021-45046).

## Improvements in 2.1.0

### Changed

* BLS: /termfreq operation no longer requires a filter query.


### New 

* Add MetadataFieldsWriter for programmatically setting the special fields
  (in addition to doing so in .blf.yaml files)

### Fixed

* Fix crash during indexing if terms file got very large.
* BLS: Fix incorrect check who user-owned formats.

## Improvements in 2.0.0

### API changes

#### BLS
* (breaking change) BLS responses will now report metadata values as arrays,
  because metadata fields can have multiple values.
* BLS now reports capture groups (in a captureGroups element for each hit) (by @severian)
* BLS now allows all headers in CORS requests
* You can now specify listvalues and/or listmetadatavalues to limit what annotations and metadata fields are returned.
  If omitted, all annotations/fields are returned.
* BLS responses now include displayNames and "tab grouping" (if any) for metadata fields (thanks @eduarddrenth)
 
#### Java library
* (breaking change) Completely refactored Java API to be more clear and consistent.
  See the migration guide.
* (only for advanced Java library usage)
  Made the concept of a BlackLab instance ("BlackLabEngine") explicit, so you can 
  e.g. decide how many threads you want your BlackLabEngine to have.

### New
* In addition to VTD-XML, Saxon may be used to parse input files for indexing. Saxon supports XPath 3, which
  contains many features useful for configuring input formats. It is also faster, at the cost of increased memory 
  usage for large files.
* Multiple values may be indexed for a metadata field (this is automatic when your XPath matches multiple values).
  (this is is the reason why BLS will now report all metadata as lists of values, even if there's only one)
* Added mapValues option to map metadata field values while indexing
* It is now possible to configure global unknown condition and value (use metadataDefaultUnknownCondition 
  and metadataDefaultUnknownValue at the top-level of your indexing config)
* You can now set isInternal to true on annotations to prevent searching and grouping on it in corpus-frontend
* Added annotation option allowDuplicateValues (defaults to true). If multipleValues is true and allowDuplicateValues 
  is false, duplicates encountered will not be indexed twice, preventing double hits.
* Add support for retrieving occurances of a list of terms, /blacklab-server/INDEX_NAME/termfreq

### Changed
* Speeded up searches by allowing hits to be fetched in parallel
* New Search system that allows better optimization and integrates result
  caching, allowing you to define an application-specific cache behaviour.
* There is a new version of the blacklab and blacklab-server configuration 
  files. The old version still works but will be removed in the future.
  See migration-guide.md for the details of the new format. 
* IndexTool now defaults to multithreaded indexing and the number of threads can be configured
* Calculating tokens in a subcorpus (using includetokencount=true on a docs query with 
  just a metadata filter) is much faster (through the use of DocValues). 
  We use DocValues in more places for performance improvements.
* In new indexes, subannotations are part of the index structure. As a result of this,
  you can enumerate the subannotations and their values much more efficiently.
  You can also specify any metadata you can specify for a regular annotation,
  such as displayName, uiType, etc. Querying subannotations should be more efficient 
  as well.
* Indexing should use less memory now.
* Sparse annotations take up less disk space.
* Forward indexes take up less memory while searching, and are initialized in the 
  background for quicker startup.
* A number of new metadata settings are supported, such as annotationGroups.
  More extensive documentation will follow.
* Added optional detailed SQLite logging for diagnosing performance issues.
* Improved error message when passing invalid regex expression or when query contains unknown annotation
* If punctPath matches an empty tag, replace it with a space. This deals with e.g. TEi <lb/> tags as punctuation.
* Don't autodetect titleField. If not set, use pidField. If that's not set, use fromInputFile.
* Allow leading wildcards in metadata filter queries
* If performance.maxThreadsPerSearch is set to 1, use HitsFromQuery instead of HitsFromQueryParallel
* Many smaller improvements.

### Fixed
* Fixed a concurrency bug that could cause a search to get stuck.
* Capture groups work with parallel hit fetching (by @severian)
* HitGroups retain capture group information (by @severian)
* Fixed and sped up determining subcorpus size (for relative frequencies) when grouping
* Restore namespace on document element when fetching part of a document (by @eduarddrenth)
* maxConcurrentSearches is now always at least 4, to avoid deadlocks on single-core machines
* Warn if value is not stored because it's too long; increase maximum value length.
  (if value exceeds 32K, it will be truncated when storing in DocValues)
* Only use NumericDocValues from a single thread at a time (reported by @severian)
* Sanitize field names containing characters not allowed in XML element names, so BLS responses are valid XML
* Fixed textDirection from config not working when creating new index
* When displaying left context, ensure the words are in original order
* Gap filling works when cql query contains a newline
* Fixed deleting documents from index, accidentally including deleted docs in results.
* Fix groups with a colon in the group name not working correctly
* Many smaller bugs in indexing and searching fixed
* Library versions updated

### Removed
* Really old index formats (pre 1.4 or so) are no longer supported.
  BlackLab will report this when you try to open such an old index. 
* BLS no longer supports the rarely used block=no parameter for 
  polling-based asynchronous search. All search requests block until
  the response is available. Only the "total results" count will be 
  reported asynchronously unless the "waitfortotal" parameters is true.

## Improvements up to v1.7.2

### New
* Issue warning if unrecognized params passed to Auth* class.

### Changed
* Rewrite queries like [lemma=".*"] to [] for efficiency

### Fixed
* Sort matches for XPaths (VTD-XML does breadth-first search and doesn't 
  necessarily return matches in document order; this can be a problem for 
  more complex XPaths).
* Made timeout longer for hits queries to avoid Timeout error.
* Jackson and commons-compress libraries were updated because of patch 
  security vulnerabilities. 

## Improvements up to v1.7.1

### Changed
* Default maximum file upload size is now 1GB. 

### Fixed
* Don't block until all hits have been counted.
* Fixed bug when maxHitsToRetrieve and maxHitsToCount are the same value

## Improvements up to v1.7.0

### New
* Much user-friendlier indexing using .json/.yaml config files.
  Added support for tabular formats and plain text.
  Includes built-in format configurations for many popular formats.
  See documentation.
* Added "linked document" indexing, which provides e.g. the ability 
  to automatically locate and index the corresponding metadata for 
  each document.
* BLS can now return results in CSV format.
* BLS now supports an "autocomplete" request for metadata fields as well as
  property values. 
* Corpora now have a text direction setting that the user interface can 
  use to change how things are displayed.
* contentViewable can now be specified at the document level as well 
  as corpus-wide, by adding a metadata field named "contentViewable"
  with the value "true" or "false". The per-document setting overrides 
  the corpus-wide setting.
* BLS now allows private corpus owners to explicitly share the corpus with
  other users.
* Many small additions to make it easier to generate a user-friendly
  user interface based on corpus structure information from BlackLab Server,
  such as displayName for fields and values, uiType for field and 
  properties, etc.
* BLS can now omit empty properties in the results if desired. Default is
  to include empty properties. Use requests.omitEmptyProperties setting to 
  change.
* BLS will now send the "Access-Control-Allow-Origin: *" header by 
  default, allowing a frontend on another server to access it.
  This can be overridden in the requests.accessControlAllowOrigin
  setting.
* BLS can attempt to generate a default XSLT from a (XML-based) input format
  configuration.
* Added a MetadataFetcher that reads from CSV files.
* Added plugin capability for document conversion and tagging before indexing,
  allowing BLS to e.g. take ePub input files, convert them to a corpus format, 
  linguistically annotate them and index them. This feature is somewhat 
  experimental and as of yet undocumented.
* Many smaller improvements.

### Changed
* BlackLab now requires Java 8.
* BLS no longer trims leading/trailing whitespace from parameter values. 

### Fixed
* Untokenized fields weren't lowercased while indexing, but were lowercased
  when searching, causing problems. Now they are treated the same.
* Numerous other bugfixes.

## Improvements up to v1.6.0

### New
* Added Searcher.getBlackLabVersion(). Also added blacklabVersion to index.
* Added Hits.filteredBy() to filter Hits on a HitProperty.

### Fixed
* Fixed bug with case-insensitive Terms.indexOf().
* Fixed buffer overrun while writing Terms file.
* Avoid problems with truncating mapped files on Windows. 
* Fixed bug when rewriting "n-grams containing at start/end ..." queries.
* Fixed incorrect matching if ANDNOT has multiple negative clauses.
* Use Arrays.hashCode instead of Object.hashCode for int arrays.
* Fixed "createWeight" bug when filtering on range queries.
* Made it possible to create, close, and re-open an empty index.
* Memory leak in BLS because of bug in TextPattern.rewrite().
* Fixed bug with regexes including character class negations.
* Fixed some rare NullPointerExceptions.

### Changed
* Added an alternative way of matching subqueries, using the forward index
  and a nondeterministic finite automaton (NFA; similar to many regular expression
  engines). Added an optimizer to choose when to use NFA matching.
  Still tweaking, but certain types of queries should be faster with this. 
* Made global constraints on capture groups possible.
* Added HitPropertyHitPosition for fast reproducible sorts.
* Made sure SpansSequenceSimple (which is fast) is used more often.
* Updated library versions, including migration to log4j 2.x.
* Indexer.index(File) now indexes all files by default instead of *.xml.
  If you want to index *.xml, use Indexer.index(File, String glob).
  IndexTool still defaults to *.xml; specify different glob if you want
  to index other file types.
* Upgraded from Lucene 5.2.1 to 5.5.2. Also made some preparations for
  eventually upgrading to Lucene 6.x.
  Deprecated methods that use the deprecated Filter and replaced them with
  a variant that takes Query.
* Removed several (long-)deprecated methods.
* SpanQueryFiltered.rewrite() rewrites its filter as well.
* BLSpanQuery is now the base class of all our SpanQuery classes, and is
  used throughout the code. Only the API hasn't been updated yet, but
  will throw IllegalArgumentException if a SpanQuery argument is not a 
  BLSpanQuery.
* Moved all TextPattern classes to nl.inl.blacklab.search package.
  Moved all SpanQuery/Spans classes to nl.inl.blacklab.search.lucene.
  Made some internal TextPattern methods package-private.
* Renamed SpanQueryBase to BLSpanQueryAbstract.
* Many improvements to documentation based on feedback and new features.
* Many optimizations, large and small.
* Many little fixes.

### BlackLab Server
* Cleaned up logging, made debug logging configurable by subject.
* Improved configuration error handling, made index scanning recursive.
* Added /explain?patt=... to explain how queries are optimized.
* Made long-running count operations pause if client doesn't check status.
* Added parameterized querying (filling in gaps with TSV data). 
* Added hitfiltercrit and hitfilterval parameters to filter hits on a criterium,
  like "word left of matched text". This allows you to view the hits in a single group
  after grouping, and then still allows you to group on these hits again, unlike the
  existing "viewgroup" parameter.
* Fixed cache bug that would cause "Cannot decrement refs, job was already 
  cleaned up!" message
* Made sure control characters are escaped properly in JSON and XML.
* Made sure regular and error output aren't mixed.
* In debug mode, include stacktrace with exception error. 
* Added support for enumerating subproperties.
* Made sure TooManyClauses is caught and dealth with properly.
* Fixed bug when trying to sort hits by a metadata field.
* Improved how context words group property expressions are interpreted.
* Added blacklabVersion to server info page.
* When grouping, show groups from large to small unless another sort is requested.
* Return clean error message when user passes empty group parameter.
* When trying to sample more hits than available, just return all hits.
* If no pattern given, return a proper error message.
* Return sample parameters for all search types, not just ungrouped hits.
* Also include "numberOfHits" stat when viewing group of documents.
* Added error message when 'viewgroup' is used without 'group'.
* Don't add whitespace into leaf XML elements.

## Improvements up to v1.5.0

### Fixed
* Two rare, subtle matching bugs in SpansExpansion and SpansPositionFilter.
* Fixed indexing bug where the compression code would occasionally get stuck in a loop.  

### Changed
* Deprecated TextPattern.toString() methods that take arguments.
* Gave HitProperty and DocProperty default toString() implementation.
* Added methods for iterating over all Lucene documents, forward index documents,
  content store documents.
* Added a test utility that can export your original corpus from the Lucene 
  index and content store (nl.inl.blacklab.testutil.ExportCorpus).
* Many RuntimeExceptions were changed to more specific subclasses like
  IllegalArgumentException or UnsupportedOperationException.
* Made it possible to add a metadata field with a fixed value to every
  document in a single IndexTool run. Useful if you want to combine multiple
  corpora into a single index: add each corpus in a separate IndexTool run,
  with a field Corpus with the appropriate name.

### BlackLab Server
* Made it possible to use POST for queries, so you can execute very large
  queries (many kilobytes). Note that very long (>30KB) regular expressions 
  can trigger problems in Lucene though.
* Allowed previously forbidden all-docs query (now that large document 
  queries are faster and less memory hungry).
* Grouped results are sorted by identity by default now.
* Made docpid a general way to easily filter on a single document PID.
* Replaced building a DataObject tree and serializing it with directly
  streaming the response data using DataStream, saving memory and time.
* Heavily refactored to be more modular.
* Removed some settings related to nonblocking mode, as they didn't seem
  very useful.
* Used JSON.org library instead of copy of the code.
* FIXED: if counting hits takes too long, don't error out but show the 
  results we have.
* FIXED: Do returns results even if counting all the results was interrupted 
  because it took too long.


## Improvements up to v1.4.1

### Fixed
* Potential overflow bug in ContentStoreFixedBlock when retrieving content.
* ContentStoreFixedBlock decompression bug that occurred with highly compressable content.
  (N.B. there is no need to re-index if you experienced either of the above bugs)

### Changed
* Don't store Document objects in DocResults, saving memory and time.
* Changed BL-CQL subproperty separator (e.g. for querying part of speech features separately)
  from ':' to '/'.

### BlackLab Server
* Made sure missing options in blacklab-server.json don't cause problems.

## Improvements up to v1.4.0

### New
* Added experimental support for "subproperties": properties that are indexed in the same Lucene 
  field, using prefixes, but don't each have a forward index. For now, mainly useful for
  indexing each part of speech feature separately, but in the future, BlackLab could possibly move 
  to indexing all properties in a single Lucene field. See DocIndexerOpenSonar,
  QueryExecutionContext.subpropPrefix(). 
* Added HitsSample and its implementation class, which can take a random sample of a larger
  set of hits.
* Added HitPropertyContextWords, which gives the user more options for sorting/grouping on
  context words, such as "group on the first and last words of the matched text" or "group on the
  second and third words to the left of the matched text".

### Changed
* Performed some code cleanups, moved some internal classes to different packages.
* Made Searcher an abstract base class to SearcherImpl; Hits an abstract base class to HitsImpl.
  Added mock classes for Searcher, Hits, ForwardIndex, Terms; used them to add tests.
* Moved hits-related settings from Searcher and Hits into a shared HitsSetting class;
  Searcher has a default set of HitsSettings that Hits objects "inherit".
* Updated gs-collections 6.1 to eclipse-collections 7.1. Replaced Map&lt;Integer, T&gt; with
  IntObjectMap, Map&lt;Integer, Integer&gt; with IntIntMap, ArrayList&lt;Integer&gt; with IntArrayList
  a number of times (mainly) for more memory-efficiency.
* Started using commons-lang to replace certain utility functions.
* Moved some basic Lucene functionality unrelated to the rest of BlackLab from Searcher to 
  LuceneUtil.

## Improvements up to v1.3.7

### Fixed
* Opening a large "fixed-block" content store took a really long time. 

## Improvements up to v1.3.6

### Fixed
* Content store growing larger than 2 GB caused an integer overflow.
* DocIndexersXmlHandlers element matching didn't work correctly for some XML structures.
* Storing document in ContentStoreDirFixedBlock would very rarely crash due to a
  block resizing bug.

## Improvements up to v1.3.5

### New
* Added default unknown condition and value to indextemplate.json, so you can specify what to do if a metadata field value is missing without specifying it for each field separately.

### Fixed
* BLSpanOrQuery would occasionally miss valid hits due to a
  bug in the advanceStartPoint() method.

### Changed
* Switched to JavaCC 6.x.

## Improvements up to v1.3.4

### Fixed
* Lone carriage return characters in JSON output were not escaped;
  Windows line endings were escaped as a single \\n.

## Improvements up to v1.3.3

### Fixed
* Indices with old terms file format (pre-1.3) produced empty concordances.

## Improvements up to v1.3.2

### Fixed
* Query rewrite bug when combining identical clauses with different repetitions,
  i.e. \[pos="AA.*"\]\[pos="AA.*"\]* --> \[pos="AA.*"\]+
* Throw a descriptive error if an index contains no fields.

### Changed
* Some small code quality improvements, like using .isEmpty() instead of .size() == 0.
* Added -javadoc and -sources JARs to Maven build, in preparation for publishing to Maven Central.
* Added distributionManagement section for deploying to OSSRH staging area. 

## Improvements up to v1.3.1

### New
* Added new default content store format "fixedblock", that improves space re-use when updating documents. 

### Fixed
* Bug in SpanQueryAnd which caused incorrect hits to be reported.

## Changed
* Special OSX and Windows files are skipped, even if they occur inside archives.

## Improvements up to v1.3

### Added
* Searcher now implements Closeable, so it can be used with the try-with-resources statement.
* You can specify that properties should not get a forward index using the complexField property "noForwardIndexProps" (space-separated list of property names) in indextemplate.json.

### Fixed
* Forward index terms is no longer limited to around 2 GB.

### Changed

## Improvements up to v1.2.1

### Fixed
* Queries containing only a document filter (metadata filter) would return incorrect results.

## Improvements up to v1.2.0

### Changed
* Switched build from Ant to Maven, and added generating a project site with javadocs, reports, etc.
* Using less memory by switching some Maps in the forward index to the gs-collections one.
* Updated to Lucene 5.2.1.
* Added Maven project site, available at http://inl.github.io/BlackLab/
* Removed Lucene query parser for corpus queries.
* Keep tag end position in payload of start tag, which results in much faster tag searches.
* Rewrote many slower queries to make them (much) faster. Particularly searches with "not" parts and "containing"/"within" should be faster.
* Sped up "containing", "within", and other such filter queries.
* TextPatternAnd was renamed and expanded to TextPatternAndNot. TextPatternAnd is still available as a synonym, but has been deprecated.
* Added TextPatternFilterNGrams to speed up queries of the form: []{2,3} containing "water" (or the "within" equivalent).
* Added BLSpans.advanceStartPosition(target) to "skip" within a document.
* Commons-compress (used for reading .gz files) is statically linked now.
* Limited token length to 1000 (really long tokens would cause problems otherwise).

### Fixed
* Empty version file causes NullPointerException.
* Missing manifest file causes NullPointerException.
* ContentStoreDir contained dependencies on the default encoding.
* A number of subtle search bugs.
* Opening an index by passing a symbolic link throws an exception.
* Miscellaneous small fixes.

## Improvements up to v1.1.0
* Upgraded from Lucene 3.6 to Lucene 4.2. This should speed up regular expression searching, among other things. The required Lucene 4 modules are: core, highlighter, queries, queryparser, analyzers-common. Thanks to Marc Kemps-Sneijders from the Meertens Institute for the code submission!
* The awkwardly-named classes RandomAccessGroup(s) were renamed to HitGroup(s). Also, DocGrouper was renamed to DocGroups to match this naming scheme. The old versions are still around but have been deprecated.
* HitPropValue classes now need a Hits object to properly serialize/deserialize their values in a way that doesn't break after re-indexing.
* Manual object construction was replaced with method calls where possible, for convenience, speed and ease of refactoring. Examples: use Hits.window() instead of new HitsWindow(); use Hits.groupedBy() instead of new ResultsGrouper(); use DocResults.groupedBy() instead of new DocGroups(). (code to the HitGroups/DocGroups APIs instead of to the concrete type ResultsGrouper/DocGrouper). Same for DocResults.
* HitGroups now iterates over HitGroup (used to iterate over Group)
* If you just want to query documents (not find hits for a pattern), use Searcher.queryDocuments(Query) (returns DocResults without hits).
* Preferably use Hits.sortedBy() (returns a new Hits instance) instead of Hits.sort() (modifies Hits instance). In a future version, we want Hits to become immutable to facilitate caching in a multithreaded application. Note that although you get a new Hits instance, the hits themselves are not all copied (no need, because the Hit class is now immutable).
* LuceneQueryParser.allowLeadingWildcard now defaults to false like in Lucene itself. Call LuceneQueryParser.setAllowLeadingWildcard() to change the setting.
* If you want to control how indexing errors are handled, subclass IndexListener and override the errorOccurred() method. This method receives information on what file couldn’t be indexed and why.
* Visibility for some internal classes and methods has been reduced from public to package-private to trim the public API footprint, promoting ease-of-use and facilitating future refactoring. This should not affect applications. If it does affect you, please let me know.
* Some other methods have been renamed, are no longer needed, etc. and have been deprecated. Deprecated methods state the preferred alternative in the @deprecated Javadoc directive.


## Improvements up to v1.0

### Features
* Sorting/grouping on multiple properties now works correctly. Use HitPropertyMultiple.
* You can now sort/group (case-/accent-) sensitively or insensitively.
* You can now easily get a larger snippet of context around a single hit (say, 100 words before and after the hit). Call Hits.getConcordance(String, Hit, int) for this purpose.
* Indexing classes now work using element handlers, making them much more readable. Supporting a new file format has become simpler as a result of this. TEI P4/P5 and FoLiA indexing classes are included with BlackLab now. See nl.inl.blacklab.indexers package.
* It is now possible to delete documents from your index. The forward indices will reuse the free space for new documents.
* The Hits class should be thread-safe now. This makes several things possible: paging through hits without re-executing the query and quickly displaying the first few hits while a background thread fetches the rest. You can even display a running counter while hits are being fetched.
* nl.inl.blacklab.tools.IndexTool is a new generic indexing tool. It is command-line utility that lets you create new indices, add documents to them and delete them again. Indexing can be customized via commandline parameters and/or a properties file. Pass --help for more info.
* QueryTool, the command-line search tool and demonstration program, has been improved with many little features, including a performance test mode.
* Long-running queries may be interrupted using Thread.interrupt(); this will stop the gathering of hits and return control to the caller.
* Hacked in (very) experimental SRU CQL (Contextual Query Language) support. Still needs a bit more love though. :-)

### Performance-/memory-related
* Concordances (for KWIC views) are constructed using the forward indices now (including the one for the new ‘punct’ property, containing punctuation and whitespace between words – if you created your own indexer, it pays to update it to include this property). Before they were constructed using the content store, but this method is much faster and more disk cache friendly. 
* Startup speed has been improved, and there is an option to automatically “warm up” forward indices (i.e. prime the disk cache) in a background thread on startup. Enable this by calling Searcher.setAutoWarmForwardIndices(true); before constructing a Searcher object. This may become the default behaviour in future versions.
* Applications have more control over the maximum number of hits to retrieve, and the maximum hits to count. “Unlimited” is also an option. By default, no more than 1M hits are retrieved, but all hits are counted.
* Several types of queries (notably, phrase searches) have been sped up using the ‘guarantee’ methods in BLSpans to determine when certain operations can be skipped.
* Several other small improvements in performance and memory use.

### Other
* Opening the BlackLab index should now be done using Searcher.open() instead of directly through constructor. See the [https://github.com/INL/BlackLab/commit/d1d1b71ca8d5ef2aea25eab5a6e12b7e51cf5f65 commit message] for the rationale.
* Several superfluous methods were deprecated to simplify the API. The Javadoc will indicate why a method was deprecated and what alternative you should use. Deprecated methods will be removed in the next major version.
* Many small bugs fixed, comments added and code structure improved.

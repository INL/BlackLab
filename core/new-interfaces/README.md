# BlackLab interfaces redesign

An attempt to design the ideal interfaces as a roadmap for future refactorings.

Goals:

- clean, consistent
- decoupled: modules should be self-contained as much as possible
- testable (e.g. isolate file system interactions, etc.)
- flexible (e.g. avoid 'new' in client code; make constructors private and provide factory methods, so we can change how stuff is instantiated later)
- enabling maximum performance; avoid 'leaky abstractions'
- enabling future Solr integration (i.e. forward index, content store and index metadata will all become part of the Lucene index, via codecs)
- feasible to refactor current BlackLab code into this

Reasoning behind specific design choices / implementation notes:

- Hits/Hit (Results/Result):
    - new interfaces will allow us to experiment with how (and even whether) to store hits inside Hits instances. Some Hit implementation won't even need their own data, but can just access arrays inside their Hits instance when needed.
    - Hits effectively immutable: this will make stuff like concurrency easier, as well as making stuff generally easier to reason about
    - Hit ephemeral/immutable: don't need to instantiate Hit instances if they're not going to be saved
    - Hits instances either sequential or random-access: this enables us to choose between just getting the hits we're interested in and ignoring the rest (or just determine some statistics about them without storing them, for example), or gathering and storing all hits (like we always do now).
    - No more storing context information in Hits: will reduce memory usage and hopefully does not degrade performance (or even improves it)
    - Hits implementation: single array containing everything?
    - Retrieve context while collecting hits, so you have the correct LeafReader available


- ForwardIndex / AnnotationForwardIndex:
    - Right now, each annotation has its own, independent forward index. It makes more sense to have a single interface to access forward index information for a document. For Solr integration, the forward indexes will be stored with each document, which also aligns with this approach. It also means we can eventually get rid of fiids.
    - Dynamically creating a forward index the first time it is accessed in index mode is problematic. It means we can't easily experiment with a forward index that stores all annotations together. We should probably require mandatory declaration of all annotations before indexing starts.
    
- BlackLabIndex / BLIndex:
    - Searcher currently represents the whole BlackLab index (Lucene index, forward index, content store, indexmetadata), but also provides methods primarily acting/relying on the Lucene index. This feels like it should be a separate interface.
    - Lucene indexes are actually split into smaller parts that can be processed separately (e.g. in parallel). It is important to enable this in BlackLab, hence the separate interface with its .leafReaders() method.
    - When ForwardIndex and ContentStore become part of the Lucene index (for Solr integration), they will also be distributed over the "leaves" of the index, so accessing them that way is the best approach to ensure maximum future performance.

- Indexing:
    - Mandatory declaration of fields(?), annotations, forward indexes.
    - Generic rollback on error. IndexMetadata is the main thing; FI/CS will solve itself when we integrate them in the Lucene index.


## Immutability (in search mode) ##

### MUTABLE ###

- BlackLabIndex
  Certain settings can be changed, changing the state of the index object.
  Mutating methods:
  setCache, setDefaultContextSize, setMaxSettings, setDefaultMatchSensitivity, 
  setCollator, setDefaultUnbalancedTagsStrategy.

### ALMOST IMMUTABLE ###
- Results class (Hits, HitGroups, DocResults, DocGroups, ...)

  When alle results have been fetched, the objects are immutable.
  Until then, methods with names ending in "SoFar" and "AtLeast" may return different values.
  All other methods should always return the same value for the same inputs, regardless of how many results have been fetched.
  
  Strictly speaking, each Results instance has a ThreadPauser that may change from unpaused to paused and back,
  but that doesn't affect the other properties, just the timing. ThreadPauser may be removed in the future.

- CapturedGroups

  Hits objects may have a CapturedGroups object if the query captures any groups.
  Whenever a Hit inside a Hits object is accessible, its corresponding CapturedGroups entry is available,
  so this instance should appear immutable to the client.

- Terms
  setBlockBasedTermsFile (should be solvable)

### IMMUTABLE ###
- Result (including Hit, DocResult, Group<>()
- Doc
- TextPattern, CompleteQuery
- Kwic, Concordance
- Contexts
- all index metadata/structure classes (IndexMetadata, AnnotatedField, Annotation, AnnotationSensitivity)
- ForwardIndex, ContentStore



## Implementation plan ##


MISC
- Clean up config files [but keep backwards compatible...?]
- Maybe SearchResult should know its Search (pass in QueryInfo..?)
- base interface DocStore for ForwardIndex, ContentStore..?
  access FI/CS using FIDoc / CSDoc (FIDoc already exists, but not used much) ?
- TermsSensitivity to access Terms with specific sensitivity?
- capture BLIndex settings in single setting object? Immutable?
- make error messages lower-level, "server busy" requires a bit more explanation to understand why BL is refusing to execute your search
- filter: predicate (maar Contexts gooien roet in het eten...)
- collocation sort properties
- docresults (abstract) / docresultsfromhits / docresultslist / -filtered 
- Whenever thread interrupted: gooi een BlackLabRuntimeException(-subclass)
  die BLS aan het eind opvangt en er een nette boodschap voor toont.
  (zorg wel dat aborted jobs uit de cache verwijderd worden, zodat we geen incorrecte counts tonen!)
- NOTE: right now, IndexStructure needs IndexReader because it detects certain things from the index.
        this implementation needs to stay supported, but become legacy. For new indexes, all metadata
        should be in the metadata file, so the new implementation will be decoupled from the index.
- think about index format versioning. right now forwardindex, contentstore, etc. have a separate version,
  but when we integrate with the Lucene index, we'll have a single Codec version. We should start moving away from
  separate versioning.
- remove support for "index template" file; using input format configs is the future. Setting up your own IndexMetadata
  from code should be possible as well, with IndexMetadataWriter.


### Design ###

- enforce equals, hashCode, compareTo, toString default implementations? (Hit, BLDocument, ...?)

### Other stuff ###

- Top comment on each interface

### Rejected ideas ###

- Stel dat we classes Hit en BLDocument helemaal laten vallen en alleen met indexes (hitId, docId) werken. Is dat haalbaar?
  Wat zou het gevolg daarvan zijn? (je moet elders een link naar de Hits / Index bewaren bijv.)
  
  Voorlopig geen goed idee; interfaces worden een stuk rommeliger (geen common base interfaces, allerlei extra accessor methods),
  en het is niet duidelijk dat het daadwerkelijk efficienter is, zeker niet als je een eigen Iterator maakt die hit-data uit Hits haalt
  (dat is basically hoe een Hit object nu al kan werken, minus de iterator-operaties)



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


## Implementation plan ##

struct
+ use the new struct interfaces (IndexMetadata, etc.)
+ use AnnotatedField / Annotation / AnnotationSensitivity everywhere

index
+ separate Searcher into read/write interfaces (including DocWriter for DocIndexers)
+ introduce Doc; replace (most) docIds with it
+ save() / isImmutable() toch voorlopig weg
+ get rid of HitsImpl(BlackLabIndex index, AnnotatedField field, HitsSettings settings);
+ ArrayList.sort is sneller dan Collections.sort, door meer kennis van interne structuur

- HitsStats class zodat je kunt linken naar de stats van de originele Spans zonder die in het geheugen te hoeven houden?
  
- HitsImpl: hit fetching extracten naar apart class..?

- (NB aborted attempt op stash en in ../tmp-bl-attempt/)
  Kunnen we niet beter HitProperty e.d. aanpassen om meer hands-on te zijn?
  D.w.z. niet alleen maar get/compare, maar echt de sort/group/filter operatie uitvoeren?
  Dan kan die de efficientste aanpak voor de specifieke situatie bepalen, bijv. door
  een lijst met Hit+Context objects te instantieren en die te sorteren.
  
  Dan kunnen we toch sortOrder een List<Hit> maken, met de voordelen van dien.


OUD IDEE:
- HitProperty vergelijkt Hits en niet Hit-original-indexes zoals nu.
- Contexts.get(Hit) om de context van een hit te krijgen.
- Hit krijgt een extra veld "originalIndex". Dit wordt eenmalig gezet.
- static method Contexts.retrieve(hits, ...) kiest, afhankelijk van de hits, de geschiktste Contexts implementatie:
  * hoogste originalIndex niet veel groter dan aantal hits: array-based, zodat lookup intern op basis van originalIndex kan gebeuren
  * hoogste originalIndex veel groter (bijv 4x of meer) dan aantal hits: hash-based, zodat lookup intern in een Map oid gedaan wordt.
    trager maar geheugen-efficienter, en voor kleinere sets geeft het niet zo.
    threshold kan best hoog liggen trouwens, want we bewaren Contexts tegenwoordig niet meer, dus ze nemen slechts tijdelijk "veel" geheugen in. Aan de andere kant kan cache-efficientie een overweging zijn als het array erg sparsely populated wordt.


- Hits.copyMaxHitsRetrieved: kunnen we deze stats niet samen met field in een soort "query info / state" objectje vatten wat we kopieren (of refereren)?
  
results

- replace DocResults with grouping by HitPropertyDoc (that has a Doc internally)
  PROBLEM: DocResults relies on the fact that results are sorted by document.
  Special class of grouping operation..?
  But can we know whether Hits are doc-sorted or not...?

- eliminate HitsWindow, have Hits contain optional window stats
- introduce base interface Results connecting Hits, HitGroups, GroupGroups; 
  also ResultProperty (HitProperty / GroupProperty)
  
search
- introduce new Search interface for building searches
- update caching in BLS

MISC
- replace separate ForwardIndexes with single ForwardIndex / AnnotationForwardIndex
  use ContentStoreDoc / ForwardIndexDoc
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



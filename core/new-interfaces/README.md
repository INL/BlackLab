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

index
+ HitProperty moet een context size parameter krijgen (indien relevant uiteraard)

- Refactor forwardindex naar multiforwardindex / forwardindexdoc

- Nieuwe (multi)forward index die documenten lineair opslaat.
  Ws. handig om dan van een document eerst alle words, dan alle lemmas, dan alle pos, etc. op te slaan.

- HitProperty e.d. aanpassen om meer hands-on te zijn?
  D.w.z. niet alleen maar get/compare, maar echt de sort/group/filter operatie uitvoeren?
  Dan kan die de efficientste aanpak voor de specifieke situatie bepalen, bijv. door
  een lijst met Hit+Context objects te instantieren en die direct te sorteren. Of indien mogelijk eerst de sortvalue bepalen voor elke Hit en daarop sorteren (als vergelijkingen duur zijn).
  (NB aborted attempt op stash en in ../tmp-bl-attempt/)
  
  Dan kunnen we toch sortOrder een List<Hit> maken, wat efficienter is en waarschijnlijk zorgt dat Results interfaces/classes cleaner en generieker blijven.

  HitProperty immutable (nu niet door context, contextIndices)
  
- filtering now cancels sort, because HitProperty uses original position. Solve after HitProperty-refactor?

- Whenever thread interrupted: gooi een BlackLabRuntimeException(-subclass)
  die BLS aan het eind opvangt en er een nette boodschap voor toont.
  (zorg wel dat aborted jobs uit de cache verwijderd worden, zodat we geen incorrecte counts tonen!)

results
- DocResults moet ook geen source hits meer vasthouden voor grand total counts. Het is aan de client om die op te vragen.
  Alternatief: toch alle stats in MaxStats zetten. Je kunt dan alleen niet aangeven dat je het grand total wilt
  weten, dus het kan zijn dat de total count job stopt tot iemand er om vraagt, en dat kan dus niet.

- DocResults, Groups moeten ook threadPauser hebben

- replace DocResults with grouping by HitPropertyDoc (that has a Doc internally)
  PROBLEM: DocResults relies on the fact that results are sorted by document.
  Special class of "as we go" grouping operation..?
  If you choose to use this grouping operation, it is up to you to make sure the hits
  are document-sorted (i.e. don't sort them by context, then apply an as-you-go grouping
  operation)

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



PERFORMANCE IMPROVEMENTS MADE SO FAR

CACHE / LONGTERM MEMORY USE
- don't hold on to references
- don't keep context info around
- calculate cache size based on number of objects (mostly hits, so reasonable approximation)
- (bug) don't empty out cache when maximum number of searches is reached

FRAGMENTATION, GC OVERHEAD PROBLEMS
- don't create extra hit objects just to add the docBase in HitsFromQuery
- don't allocate Hits to sort them in SpansInBucketsAbstract; instead use fastutil's LongArrayList
  (with start/end of a span encoded in a long) and sort it using LongArrays.quickSort().





FEATURE KOEN:
- sort/group sensitivity for all properties




fastutil ipv eclipse collections...???



TIMEOUT
- TREEDT ER TOCH NOG EEN PASSENGER TIMEOUT OP? LANGE SEARCH (140s, 50s count) OP GREYLAB BLIJFT AFBREKEN
- ProxyTimeout, Timeout opschroeven naar 1800
- BLS search timeout opschroeven (was 300);


MEMORY / GC
- memory fluctueert enorm bij enigszins flinke searches. hoe komt dat?
- sommige queries zetten alle cores op 100%, ook al is BL nog steeds redelijk single-core; is dat GC?


CPU
- beter gebruik maken van parallellisme voor itereren over grote hitsets
  leafreaders parallel processen (alleen als we vantevoren weten dat we alle hits gaan ophalen - 
  beetje slim mee omgaan, zodat je niet per ongeluk de trage variant kiest als de volgorde van aanroepen niet optimaal is)


MEMORY EFFICIENCY / CACHING
- worthiness: laat grootte ook meespelen, zodat counts langer bewaard worden dan bijv. grote groupings
  laat search time ook meespelen, zodat snel te herhalen searches eerder weggegooid worden
- viewgroup / filter (is dezelfde operatie): laat het afhangen van property (context / metadataveld; metadata kan met 
  filterQuery, context kan soms ook met CQL) en beschikbaarheid in cache wat je toont. kijk ook naar 
  of de groepen volledig opgeslagen zijn.
- sla niet altijd alle hits in een grouping op, alleen de eerste X (bijv. als er meer dan 100K hits zijn oid).
  sla de totalen van grouping altijd los op, zodat die in ieder geval lang bewaard kunnen blijven.
  als hits volledig opgehaald zijn altijd automatisch het totaal in de cache opslaan
- kunnen we zorgen dat sorted hits in cache gebruikt kunnen worden alsof het unsorted hits zijn? dan kunnen we
  unsorted er uit gooien zodra we sorted gemaakt hebben. (als je per doc results wilt maken, moet je wel eerst op docid sorteren)



PERFORMANCE ANALYSIS
- zet cache monitoring tool op server
- JConsole/VisualVM connecten. Eerst lokaal draaiend krijgen, dan pas op server?
- log queries, times, #results, memory, etc. to separate log file for analysis
  also log whether search was aborted (, paused)
  log verschillende fases van uitvoeren query met de tijden (lucene, hits, sort/group, ...)
- bouw evt. nieuwe server met nieuwe blacklab, zodat we sneller kunnen vergelijken?
- maybe add a performance logging object to QueryInfo, so you can gather detailed information about
  the different phases of queries, and see what is slowing things down


SERVER POLICIES
- max. number of running searches (if you try to start one but already at max, you get an error message)
  (probably needs to take into account how many cores are in use)
- max. hits to process per search (if exceeded, BLS will indicate this)
- max. time a search may run (after which it is aborted by BLS and an error message is shown)
- number of result instances in cache
  (result instance = hit, doc, group, etc.; measure of amount of memory taken)
  (if exceeded, a result is selected to be removed based on size, staleness, search type)
- searches are automatically removed from cache after X time, even if cache is not full
  (to allow GC to reclaim memory)
- min. amount of free memory (if less, we try to remove cached searches, and you cannot start a new search)



BUG
- FOUT, ZIT OOK AL IN 1.7: (negatieve match met NFA werkt niet goed, golf komt toch voor)
```
http://localhost:8080/blacklab-server/opensonar/hits?number=20&first=0&patt=%22de%22+[lemma+!%3D+%22golf%22]{2}+%22daarbij%22+%22ontstond%22&sort=hit%3Alemma&_=1534601373896&explain=yes
```






POSSIBLE OPTIMIZATIONS
- getAbandonedCountPauseTimeSec / getAbandonedCountAbortTimeSec

- (eventually: don't use threads except for total count (the only asynchronously running search, right...?) )

- what if we do a search, start a totalcount that doesn't store hits, then decide we want to group the hits?
  should we terminate the totalcount...?
  or should we always start out storing hits, and only transition to not storing but just counting if we haven't
  used them after a while..? (this means we need to track hit use in Hits)

- (CPU/threading) voor operaties waarvan we zeker weten dat we alle hits nodig hebben:
  haal hits in parallel op voor meerdere/alle leafreaders. verwerk ze verder ook in parallel waar mogelijk.

- niet alle resultaten opslaan in groupings. en/of na een tijdje groups "truncaten" zodat er minder/geen
  resultaten opgeslagen blijven.
  kun je gebruiken voor docs/snippets maar ook voor andere groupings: bewaar de eerste 25 en toon die
  als mensen groep openklikken; zoek en toon de rest pas als ze de hele groep willen zien.
  je kunt de volledige grouping evt. korter in de cache bewaren (of helemaal niet) en de truncated grouping langer

- Nieuwe (multi)forward index die documenten lineair opslaat.
  Ws. handig om dan van een document eerst alle words, dan alle lemmas, dan alle pos, etc. op te slaan.
  performancevoordelen: forward index matching; lazy filtering (which we don't do right now...)

- HitProperty evt. aanpassen om meer hands-on te zijn?
  D.w.z. niet alleen maar get/compare, maar echt de sort/group/filter operatie uitvoeren?
  Dan kan die de efficientste aanpak voor de specifieke situatie bepalen, bijv. door
  een lijst met Hit+Context objects te instantieren en die direct te sorteren. Of indien mogelijk eerst de sortvalue bepalen voor elke Hit en 
  daarop sorteren (als vergelijkingen duur zijn).
  


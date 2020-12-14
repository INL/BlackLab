package nl.inl.blacklab.server.requesthandlers;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DocValuesTermsQuery;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.exceptions.RegexpTooLarge;
import nl.inl.blacklab.exceptions.WildcardTermTooBroad;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyDoc;
import nl.inl.blacklab.resultproperty.HitPropertyDocumentId;
import nl.inl.blacklab.resultproperty.HitPropertyDocumentStoredField;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.resultproperty.HitPropertyMultiple;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.PropertyValueMultiple;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.Doc;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.QueryExplanation;
import nl.inl.blacklab.search.SingleDocIdFilter;
import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.TermFrequency;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.Concordances;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.HitGroup;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.Kwics;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.ResultCount;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.search.results.ResultsStats;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternAnd;
import nl.inl.blacklab.search.textpattern.TextPatternAnnotation;
import nl.inl.blacklab.search.textpattern.TextPatternSensitive;
import nl.inl.blacklab.search.textpattern.TextPatternTerm;
import nl.inl.blacklab.searches.SearchEmpty;
import nl.inl.blacklab.searches.SearchHitGroupsFromHits;
import nl.inl.blacklab.searches.SearchHits;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.ContextSettings;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.jobs.WindowSettings;
import nl.inl.blacklab.server.search.BlsCacheEntry;
import nl.inl.blacklab.server.util.BlsUtils;

/**
 * Request handler for hit results.
 */
public class RequestHandlerHits extends RequestHandler {

    public RequestHandlerHits(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @SuppressWarnings("unchecked")
    @Override
    public int handle(DataStream ds) throws BlsException {
        // Do we want to view a single group after grouping?
        String groupBy = searchParam.getString("group");
        if (groupBy == null)
            groupBy = "";
        String viewGroup = searchParam.getString("viewgroup");
        if (viewGroup == null)
            viewGroup = "";
        
        BlsCacheEntry<?> job;
        Hits hits;
        ResultsStats hitsCount;
        ResultsStats docsCount;

        try {
            if (groupBy.length() > 0 && viewGroup.length() > 0) {  
                Pair<BlsCacheEntry<?>, Hits> res = getHitsFromGroup(groupBy, viewGroup); 
                job = res.getLeft();
                hits = res.getRight();
                // The hits are already complete - get the stats directly.
                hitsCount = hits.hitsStats();
                docsCount = hits.docsStats();
            } else {
                job = searchMan.searchNonBlocking(user, searchParam.hitsCount()); // always launch totals nonblocking!
                hits = searchMan.search(user, searchParam.hitsSample());
                hitsCount = ((BlsCacheEntry<ResultCount>)job).get(); 
                docsCount = searchMan.search(user, searchParam.docsCount());
                
                // Wait until all hits have been counted.
                if (searchParam.getBoolean("waitfortotal")) {
                    hitsCount.countedTotal();
                    docsCount.countedTotal();
                }
            } 
        } catch (InterruptedException | ExecutionException | InvalidQuery e) {
            throw RequestHandler.translateSearchException(e);
        }
        
        if (searchParam.getString("calc").equals("colloc")) {
            dataStreamCollocations(ds, hits);
            return HTTP_OK;
        }

            
        WindowSettings windowSettings = searchParam.getWindowSettings();
        if (!hits.hitsStats().processedAtLeast(windowSettings.first()))
            throw new BadRequest("HIT_NUMBER_OUT_OF_RANGE", "Non-existent hit number specified.");
        
        Hits window = hits.window(windowSettings.first(), windowSettings.size());
        
        
        DocResults perDocResults = null;

        BlackLabIndex index = hits.index();

        boolean includeTokenCount = searchParam.getBoolean("includetokencount");
        long totalTokens = -1;
        if (includeTokenCount) {
            perDocResults = hits.perDocResults(Results.NO_LIMIT);
            // Determine total number of tokens in result set
            totalTokens = perDocResults.subcorpusSize().getTokens();
        }


        searchLogger.setResultsFound(hitsCount.processedSoFar());

        // Search is done; construct the results object

        ds.startMap();

        // The summary
        ds.startEntry("summary").startMap();

        long totalTime = job.threwException() ? -1 : job.timeUserWaited();

        // TODO timing is now broken because we always retrieve total and use a window on top of it,
        // so we can no longer differentiate the total time from the time to retrieve the requested window
        addSummaryCommonFields(ds, searchParam, job.timeUserWaited(), totalTime, null, window.windowStats());
        addNumberOfResultsSummaryTotalHits(ds, hitsCount, docsCount, totalTime < 0, null);
        if (includeTokenCount)
            ds.entry("tokensInMatchingDocuments", totalTokens);
        
        ds.startEntry("docFields");
        RequestHandler.dataStreamDocFields(ds, index.metadata());
        ds.endEntry();
        
        ds.startEntry("metadataFieldDisplayNames");
        RequestHandler.dataStreamMetadataFieldDisplayNames(ds, index.metadata());
        ds.endEntry();

        if (searchParam.getBoolean("explain")) {
            TextPattern tp = searchParam.getPattern();
            try {
                BLSpanQuery q = tp.toQuery(QueryInfo.create(index));
                QueryExplanation explanation = index.explain(q);
                ds.startEntry("explanation").startMap()
                        .entry("originalQuery", explanation.originalQuery())
                        .entry("rewrittenQuery", explanation.rewrittenQuery())
                        .endMap().endEntry();
            } catch (RegexpTooLarge e) {
                throw new BadRequest("REGEXP_TOO_LARGE", "Regular expression too large.");
            } catch (WildcardTermTooBroad e) {
                throw BlsException.wildcardTermTooBroad(e);
            } catch (InvalidQuery e) {
                throw new BadRequest("INVALID_QUERY", e.getMessage());
            }
        }
        ds.endMap().endEntry();

        ds.startEntry("hits").startList();
        Map<Integer, String> pids = new HashMap<>();
        ContextSettings contextSettings = searchParam.getContextSettings();
        Concordances concordances = null;
        Kwics kwics = null;
        Set<Annotation> annotationsToList = new HashSet<>(getAnnotationsToWrite());
        if (contextSettings.concType() == ConcordanceType.CONTENT_STORE)
            concordances = window.concordances(contextSettings.size(), ConcordanceType.CONTENT_STORE);
        else
            kwics = window.kwics(contextSettings.size());

        Set<MetadataField> metadataFieldsTolist = new HashSet<>(this.getMetadataToWrite());
        for (Hit hit : window) {
            ds.startItem("hit").startMap();

            // Find pid
            String pid = pids.get(hit.doc());
            if (pid == null) {
                Document document = index.doc(hit.doc()).luceneDoc();
                pid = getDocumentPid(index, hit.doc(), document);
                pids.put(hit.doc(), pid);
            }

            // TODO: use RequestHandlerDocSnippet.getHitOrFragmentInfo()

            // Add basic hit info
            ds.entry("docPid", pid);
            ds.entry("start", hit.start());
            ds.entry("end", hit.end());

            if (window.hasCapturedGroups()) {
                Map<String, Span> capturedGroups = window.capturedGroups().getMap(hit);
                ds.startEntry("captureGroups").startList();

                for (Map.Entry<String, Span> capturedGroup : capturedGroups.entrySet()) {
                    if (capturedGroup.getValue() != null) {
                        ds.startItem("group").startMap();
                        ds.entry("name", capturedGroup.getKey());
                        ds.entry("start", capturedGroup.getValue().start());
                        ds.entry("end", capturedGroup.getValue().end());
                        ds.endMap().endItem();
                    }
                }

                ds.endList().endEntry();
            }

            if (contextSettings.concType() == ConcordanceType.CONTENT_STORE) {
                // Add concordance from original XML
                Concordance c = concordances.get(hit);
                ds.startEntry("left").plain(c.left()).endEntry()
                        .startEntry("match").plain(c.match()).endEntry()
                        .startEntry("right").plain(c.right()).endEntry();
            } else {
                // Add KWIC info
                Kwic c = kwics.get(hit);
                ds.startEntry("left").contextList(c.annotations(), annotationsToList, c.left()).endEntry()
                        .startEntry("match").contextList(c.annotations(), annotationsToList, c.match()).endEntry()
                        .startEntry("right").contextList(c.annotations(), annotationsToList, c.right()).endEntry();
            }
            ds.endMap().endItem();
        }
        ds.endList().endEntry();

        ds.startEntry("docInfos").startMap();
        //DataObjectMapAttribute docInfos = new DataObjectMapAttribute("docInfo", "pid");
        MutableIntSet docsDone = new IntHashSet();
        Document doc = null;
        String lastPid = "";
        for (Hit hit : window) {
            String pid = pids.get(hit.doc());

            // Add document info if we didn't already
            if (!docsDone.contains(hit.doc())) {
                docsDone.add(hit.doc());
                ds.startAttrEntry("docInfo", "pid", pid);
                if (!pid.equals(lastPid)) {
                    doc = index.doc(hit.doc()).luceneDoc();
                    lastPid = pid;
                }
                dataStreamDocumentInfo(ds, index, doc, metadataFieldsTolist);
                ds.endAttrEntry();
            }
        }
        ds.endMap().endEntry();

        if (searchParam.hasFacets()) {
            // Now, group the docs according to the requested facets.
            if (perDocResults == null)
                perDocResults = hits.perDocResults(Results.NO_LIMIT);
            ds.startEntry("facets");
            dataStreamFacets(ds, perDocResults, searchParam.facets());
            ds.endEntry();
        }

        ds.endMap();

        return HTTP_OK;
    }

    private void dataStreamCollocations(DataStream ds, Hits originalHits) {
        ContextSize contextSize = ContextSize.get(searchParam.getInteger("wordsaroundhit"));
        ds.startMap().startEntry("tokenFrequencies").startMap();
        MatchSensitivity sensitivity = MatchSensitivity.caseAndDiacriticsSensitive(searchParam.getBoolean("sensitive"));
        TermFrequencyList tfl = originalHits.collocations(originalHits.field().mainAnnotation(), contextSize,
                sensitivity);
        for (TermFrequency tf : tfl) {
            ds.attrEntry("token", "text", tf.term, tf.frequency);
        }
        ds.endMap().endEntry().endMap();
    }

    /**
     * Translate the normal Hits query in the searchparams object into a query yielding only those Hits in the group with the specified PropertyValue
     * 
     * @param viewGroupVal
     * @param groupByProp
     * @param hitsGrouped check whether the original hitGroups 
     * 
     * @return the SearchHits that will yield the hits, or null if the search could not be reconstructed.
     * @throws BlsException
     * @throws InvalidQuery
     */
    private SearchHits getQueryForHitsInSpecificGroupOnly(PropertyValue viewGroupVal, HitProperty groupByProp, HitGroups hitsGrouped) throws BlsException, InvalidQuery {
        // see if we can enhance this query
        if (hitsGrouped.isSample())
            return null;
        
        // see if this query matches only singular tokens
        // (we can't enhance multi-token queries such as ngrams yet)
        TextPattern tp = searchParam.getPattern();
        if (!tp.toQuery(QueryInfo.create(blIndex())).producesSingleTokens())
            return null;
        
        // Alright, the original query for the Hits lends itself to enhancement. 
        // Create the Query that will do the metadata filtering portion. (Token filtering is done through the TextPattern above) 
        BooleanQuery.Builder fqb = new BooleanQuery.Builder();
        if (searchParam.getFilterQuery() != null) {
            fqb.add(searchParam.getFilterQuery(), Occur.FILTER);
        }

        // Decode the grouping properties, and the values for those properties in the requested group.
        // So we can enhance the BooleanQuery and TextPattern with these  criteria
        List<PropertyValue> vals = viewGroupVal instanceof PropertyValueMultiple ? ((PropertyValueMultiple) viewGroupVal).values() : Arrays.asList(viewGroupVal); 
        List<HitProperty> props = groupByProp instanceof HitPropertyMultiple ? ((HitPropertyMultiple) groupByProp).props() : Arrays.asList(groupByProp);
        
        int i = 0;
        for (HitProperty p : props) {
            if (p instanceof HitPropertyHitText) {
                String valueForAnnotation = vals.get(i).toString(); 
                HitPropertyHitText prop = ((HitPropertyHitText) p);
                Annotation annot = prop.needsContext().get(0);
                MatchSensitivity sensitivity = prop.getSensitivities().get(0);
                
                tp = new TextPatternAnd(tp, new TextPatternAnnotation(annot.name(), new TextPatternSensitive(sensitivity, new TextPatternTerm(valueForAnnotation))));
            } else if (p instanceof HitPropertyDoc || p instanceof HitPropertyDocumentId) {
                Object value = vals.get(i).value();
                int luceneDocId = value instanceof Doc ? ((Doc) value).id(): BlsUtils.getDocIdFromPid(blIndex(), (String) value);
                fqb.add(new SingleDocIdFilter(luceneDocId), Occur.FILTER);
            } 
            else if (p instanceof HitPropertyDocumentStoredField) {
                fqb.add(new DocValuesTermsQuery(((HitPropertyDocumentStoredField) p).fieldName(), (String) vals.get(i).value()), Occur.FILTER);
            } else {
                logger.debug("Cannot merge group specifier into query: {} with value {}", p, vals.get(i));
                return null;
            }
            
            ++i;
        }
        
        // All specifiers merged! 
        // Construct the query that will get us our hits.
        SearchEmpty search = blIndex().search(blIndex().mainAnnotatedField(), searchParam.getUseCache(), searchLogger);
        BLSpanQuery query = tp.toQuery(QueryInfo.create(blIndex(), blIndex().mainAnnotatedField()), fqb.build());
        SearchHits hits = search.find(query, SearchSettings.defaults());
        return hits;
    }
    
    private Pair<BlsCacheEntry<?>, Hits> getHitsFromGroup(String groupBy, String viewGroup) throws InterruptedException, ExecutionException, InvalidQuery, BlsException {
        PropertyValue viewGroupVal = PropertyValue.deserialize(blIndex(), blIndex().mainAnnotatedField(), viewGroup);
        if (viewGroupVal == null)
            throw new BadRequest("ERROR_IN_GROUP_VALUE", "Cannot deserialize group value: " + viewGroup);
        BlsCacheEntry<HitGroups> jobHitGroups = searchMan.searchNonBlocking(user, searchParam.hitsGrouped());
        HitGroups hitGroups = jobHitGroups.get();
        HitGroup group = hitGroups.get(viewGroupVal);
        if (group == null)
            throw new BadRequest("GROUP_NOT_FOUND", "Group not found: " + viewGroup);

        Hits hits = null;
        if (group.storedResults().size() > 0) { // good, the group has its backing hits available, return those. 
            hits = group.storedResults();
        }
        
        // Not all results were actually stored. Fire a separate query to retrieve them.
        if (group.storedResults().size() == 0) { 
            HitProperty groupByProp = HitProperty.deserialize(blIndex(), blIndex().mainAnnotatedField(), groupBy);
            SearchHits findHitsFromOnlyRequestedGroup = getQueryForHitsInSpecificGroupOnly(viewGroupVal, groupByProp, hitGroups);
            if (findHitsFromOnlyRequestedGroup != null) {
                // place the group-contents query in the cache and return the results.
                BlsCacheEntry<Hits> jobHits = searchMan.searchNonBlocking(user, findHitsFromOnlyRequestedGroup);
                return Pair.of(jobHits, jobHits.get());
            }
            
            // This is a special case: 
            // Since the group we got from the cached results didn't contain the hits, we need to get the hits from their original query
            // and then group them here (using a different code path, since the normal code path  doesn't always store the hits due to performance).
            SearchHitGroupsFromHits searchGroups = (SearchHitGroupsFromHits) searchParam.hitsSample().group(groupByProp, Hits.NO_LIMIT);
            searchGroups.forceStoreHits();
            // now run the separate grouping search, making sure not to actually store the hits.
            // Sorting of the resultant groups is not applied, but is also not required because the groups aren't shown, only their contents.
            // If a later query requests the groups in a sorted order, the cache will ensure these results become the input to that query anyway, so worst case we just deferred the work.
            jobHitGroups = searchMan.searchNonBlocking(user, searchGroups); // place groups with hits in search cache 
            hits = jobHitGroups
                .get() //get grouped results
                .get(viewGroupVal) // get group 
                .storedResults(); // get results in group
        }
        
        // NOTE: sortBy is automatically applied to regular results, but not to results within groups
        // See ResultsGrouper::init (uses hits.getByOriginalOrder(i)) and DocResults::constructor
        // Also see SearchParams (hitsSortSettings, docSortSettings, hitGroupsSortSettings, docGroupsSortSettings)
        // There is probably no reason why we can't just sort/use the sort of the input results, but we need some more testing to see if everything is correct if we change this
        String sortBy = searchParam.getString("sort");
        HitProperty sortProp = (sortBy != null && !sortBy.isEmpty())
                ? HitProperty.deserialize(hits, sortBy)
                : null;

        if (sortProp != null)
            hits = hits.sort(sortProp);

        return Pair.of(jobHitGroups, hits);
    }
}

package nl.inl.blacklab.server.requesthandlers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DocValuesTermsQuery;

import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyDoc;
import nl.inl.blacklab.resultproperty.HitPropertyDocumentId;
import nl.inl.blacklab.resultproperty.HitPropertyDocumentStoredField;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.resultproperty.HitPropertyMultiple;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.PropertyValueMultiple;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.QueryExplanation;
import nl.inl.blacklab.search.SingleDocIdFilter;
import nl.inl.blacklab.search.TermFrequency;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.HitGroup;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.search.results.ResultsStats;
import nl.inl.blacklab.search.results.ResultsStatsStatic;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternAnd;
import nl.inl.blacklab.search.textpattern.TextPatternAnnotation;
import nl.inl.blacklab.search.textpattern.TextPatternSensitive;
import nl.inl.blacklab.search.textpattern.TextPatternTerm;
import nl.inl.blacklab.searches.SearchCacheEntry;
import nl.inl.blacklab.searches.SearchCount;
import nl.inl.blacklab.searches.SearchEmpty;
import nl.inl.blacklab.searches.SearchHitGroupsFromHits;
import nl.inl.blacklab.searches.SearchHits;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.ContextSettings;
import nl.inl.blacklab.server.lib.ConcordanceContext;
import nl.inl.blacklab.server.lib.SearchTimings;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.jobs.WindowSettings;
import nl.inl.blacklab.server.lib.WebserviceOperations;
import nl.inl.blacklab.server.util.BlsUtils;

/**
 * Request handler for hit results.
 */
public class RequestHandlerHits extends RequestHandler {

    private static final Logger logger = LogManager.getLogger(RequestHandlerHits.class);

    public RequestHandlerHits(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @SuppressWarnings("unchecked")
    @Override
    public int handle(DataStream ds) throws BlsException, InvalidQuery {
        // Do we want to view a single group after grouping?
        String groupBy = params.getGroupProps().orElse("");
        String viewGroup = params.getViewGroup().orElse("");

        SearchCacheEntry<?> cacheEntry;
        Hits hits;
        ResultsStats hitsStats = null; // [running] hits count
        ResultsStats docsStats = null; // [running] docs count

        boolean viewingGroup = groupBy.length() > 0 && viewGroup.length() > 0;
        boolean waitForTotal = params.getWaitForTotal();
        try {
            if (viewingGroup) {
                // We're viewing a single group. Get the hits from the grouping results.
                Pair<SearchCacheEntry<?>, Hits> res = getHitsFromGroup(viewGroup);
                cacheEntry = res.getLeft();
                hits = res.getRight();
                // The hits are already complete - get the stats directly.
                hitsStats = hits.hitsStats();
                docsStats = hits.docsStats();
            } else {
                // Regular hits request.
                // Create the search objects
                SearchHits searchHits = params.hitsSample();
                SearchCount searchHitCount = searchHits.hitCount();
                SearchCount searchDocCount = searchHits.docCount();
                // Start the search.
                // - First start the hit count, which will start the underlying hits search.
                // - Then get the underlying hits search from the cache (this may take a while as
                //   it will complete when the Hits object is available)
                cacheEntry = searchHitCount.executeAsync();
                hits = searchHits.execute();
                try {
                    hitsStats = ((SearchCacheEntry<ResultsStats>) cacheEntry).peek();
                    docsStats = searchDocCount.executeAsync().peek();
                    // Wait until all hits have been counted.
                    if (waitForTotal) {
                        hitsStats.countedTotal();
                        docsStats.countedTotal();
                    }
                } catch (InterruptedSearch e) {
                    // Our count was probably aborted.
                    logger.debug("Error getting count(s)", e);
                    if (hitsStats == null)
                        hitsStats = ResultsStatsStatic.INVALID;
                    if (docsStats == null)
                        docsStats = ResultsStatsStatic.INVALID;
                }
            }
        } catch (InterruptedException | ExecutionException | InvalidQuery e) {
            logger.debug("Searching threw an exception", e);
            throw RequestHandler.translateSearchException(e);
        }

        if (params.getCalculateCollocations()) {
            dataStreamCollocations(ds, hits);
            return HTTP_OK;
        }


        WindowSettings windowSettings = params.windowSettings();
        if (!hits.hitsStats().processedAtLeast(windowSettings.first()))
            throw new BadRequest("HIT_NUMBER_OUT_OF_RANGE", "Non-existent hit number specified.");

        SearchCacheEntry<Hits> cacheEntryWindow = null;
        Hits window;
        if (!viewingGroup) {
            // Request the window of hits we're interested in.
            // (we hold on to the cache entry so that we can differentiate between search and count time later)
            cacheEntryWindow = params.hitsWindow().executeAsync();
            try {
                window = cacheEntryWindow.get(); // blocks until requested hits window is available
            } catch (InterruptedException | ExecutionException e) {
                throw RequestHandler.translateSearchException(e);
            }
        } else {
            // We're viewing a single group in a grouping result. Just get the hits window directly.
            window = hits.window(windowSettings.first(), windowSettings.size());
        }

        boolean includeTokenCount = params.getIncludeTokenCount();
        long totalTokens = -1;
        if (includeTokenCount) {
            DocResults perDocResults = hits.perDocResults(Results.NO_LIMIT);
            // Determine total number of tokens in result set
            totalTokens = perDocResults.subcorpusSize().getTokens();
        }

        // Find KWICs/concordances from forward index or original XML
        // (note that on large indexes, this can actually take significant time)
        long startTimeKwicsMs = System.currentTimeMillis();
        ContextSettings contextSettings = params.contextSettings();
        ConcordanceContext concordanceContext = ConcordanceContext.get(window, contextSettings.concType(), contextSettings.size());
        long kwicTimeMs = System.currentTimeMillis() - startTimeKwicsMs;

        // Search is done; construct the results object

        ds.startMap();

        // The summary
        BlackLabIndex index = hits.index();
        ds.startEntry("summary").startMap();
        // Search time should be time user (originally) had to wait for the response to this request.
        // Count time is the time it took (or is taking) to iterate through all the results to count the total.
        long searchTime = (cacheEntryWindow == null ? cacheEntry.timer().time() : cacheEntryWindow.timer().time()) + kwicTimeMs;
        long countTime = cacheEntry.threwException() ? -1 : cacheEntry.timer().time();
        logger.info("Total search time is:{} ms", searchTime);
        SearchTimings timings = new SearchTimings(searchTime, countTime);
        datastreamSummaryCommonFields(ds, params, timings, null, window.windowStats());
        datastreamNumberOfResultsSummaryTotalHits(ds, hitsStats, docsStats, waitForTotal, countTime < 0, null);
        if (includeTokenCount)
            ds.entry("tokensInMatchingDocuments", totalTokens);

        datastreamMetadataFieldInfo(ds, index);

        if (params.getExplain()) {
            TextPattern tp = params.pattern().get();
            try {
                BLSpanQuery q = tp.toQuery(QueryInfo.create(index));
                QueryExplanation explanation = index.explain(q);
                ds.startEntry("explanation").startMap()
                        .entry("originalQuery", explanation.originalQuery())
                        .entry("rewrittenQuery", explanation.rewrittenQuery())
                        .endMap().endEntry();
            } catch (InvalidQuery e) {
                throw new BadRequest("INVALID_QUERY", e.getMessage());
            }
        }
        ds.endMap().endEntry();

        Map<Integer, Document> luceneDocs = new HashMap<>();
        datastreamHits(ds, window, concordanceContext, luceneDocs);
        datastreamDocInfos(ds, index, luceneDocs, WebserviceOperations.getMetadataToWrite(blIndex(), params));

        if (params.hasFacets()) {
            // Now, group the docs according to the requested facets.
            ds.startEntry("facets");
            dataStreamFacets(ds, params.facets());
            ds.endEntry();
        }

        ds.endMap();

        return HTTP_OK;
    }

    private void dataStreamCollocations(DataStream ds, Hits originalHits) {
        ContextSize contextSize = ContextSize.get(params.getWordsAroundHit());
        ds.startMap().startEntry("tokenFrequencies").startMap();
        MatchSensitivity sensitivity = MatchSensitivity.caseAndDiacriticsSensitive(params.getSensitive());
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
     * @param viewGroupVal identity of group to view
     * @param hitsGrouped grouped hits
     *
     * @return the SearchHits that will yield the hits, or null if the search could not be reconstructed.
     */
    private SearchHits getQueryForHitsInSpecificGroupOnly(PropertyValue viewGroupVal, HitGroups hitsGrouped) throws BlsException, InvalidQuery {
        // see if we can enhance this query
        if (hitsGrouped.isSample())
            return null;

        HitProperty groupByProp = hitsGrouped.groupCriteria();

        // see if this query matches only singular tokens
        // (we can't enhance multi-token queries such as ngrams yet)
        TextPattern tp = params.pattern().get();
        if (!tp.toQuery(QueryInfo.create(blIndex())).producesSingleTokens())
            return null;

        // Alright, the original query for the Hits lends itself to enhancement.
        // Create the Query that will do the metadata filtering portion. (Token filtering is done through the TextPattern above)
        BooleanQuery.Builder fqb = new BooleanQuery.Builder();
        boolean usedFilter = false;
        if (params.filterQuery() != null) {
            fqb.add(params.filterQuery(), Occur.FILTER);
            usedFilter = true;
        }

        // Decode the grouping properties, and the values for those properties in the requested group.
        // So we can enhance the BooleanQuery and TextPattern with these  criteria
        List<PropertyValue> vals = viewGroupVal instanceof PropertyValueMultiple ? viewGroupVal.values() : List.of(viewGroupVal);
        List<HitProperty> props = groupByProp instanceof HitPropertyMultiple ? groupByProp.props() : List.of(groupByProp);

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
                int luceneDocId = value instanceof Integer ? ((int) value): BlsUtils.getDocIdFromPid(blIndex(), (String) value);
                fqb.add(new SingleDocIdFilter(luceneDocId), Occur.FILTER);
                usedFilter = true;
            }
            else if (p instanceof HitPropertyDocumentStoredField) {
                fqb.add(new DocValuesTermsQuery(((HitPropertyDocumentStoredField) p).fieldName(), (String) vals.get(i).value()), Occur.FILTER);
                usedFilter = true;
            } else {
                logger.debug("Cannot merge group specifier into query: {} with value {}", p, vals.get(i));
                return null;
            }

            ++i;
        }

        // All specifiers merged!
        // Construct the query that will get us our hits.
        SearchEmpty search = blIndex().search(blIndex().mainAnnotatedField(), params.useCache());
        QueryInfo queryInfo = QueryInfo.create(blIndex(), blIndex().mainAnnotatedField());
        BLSpanQuery query = usedFilter ? tp.toQuery(queryInfo, fqb.build()) : tp.toQuery(queryInfo);
        SearchHits hits = search.find(query, params.searchSettings());
        if (params.hitsSortSettings() != null) {
            hits = hits.sort(params.hitsSortSettings().sortBy());
        }
        if (params.sampleSettings() != null) {
            hits = hits.sample(params.sampleSettings());
        }
        return hits;
    }

    private Pair<SearchCacheEntry<?>, Hits> getHitsFromGroup(String viewGroup) throws InterruptedException, ExecutionException, InvalidQuery, BlsException {
        PropertyValue viewGroupVal = PropertyValue.deserialize(blIndex(), blIndex().mainAnnotatedField(), viewGroup);
        if (viewGroupVal == null)
            throw new BadRequest("ERROR_IN_GROUP_VALUE", "Cannot deserialize group value: " + viewGroup);
        SearchCacheEntry<HitGroups> jobHitGroups = params.hitsGroupedStats().executeAsync();
        HitGroups hitGroups = jobHitGroups.get();
        HitGroup group = hitGroups.get(viewGroupVal);
        if (group == null)
            throw new BadRequest("GROUP_NOT_FOUND", "Group not found: " + viewGroup);

        Hits hits;
        // Groups don't always store their backing hits (see HitGroupsTokenFrequencies for example)
        // When the group has some hits available, show those (the rest may have been culled on purpose due to maximum result limitations)
        // Only launch a separate search when there are ZERO hits stored in the group
        if (group.storedResults().size() > 0) {
            // Some hits available: return those.
            hits = group.storedResults();
        } else {
            // No results were actually stored. Fire a separate query to retrieve them.
            SearchHits findHitsFromOnlyRequestedGroup = getQueryForHitsInSpecificGroupOnly(viewGroupVal, hitGroups);
            if (findHitsFromOnlyRequestedGroup != null) {
                // place the group-contents query in the cache and return the results.
                SearchCacheEntry<ResultsStats> cacheEntry = findHitsFromOnlyRequestedGroup.count().executeAsync();
                hits = (findHitsFromOnlyRequestedGroup.executeAsync()).get();
                return Pair.of(cacheEntry, hits);
            }

            // This is a special case:
            // Since the group we got from the cached results didn't contain the hits, we need to get the hits from their original query
            // and then group them here (using a different code path, since the normal code path  doesn't always store the hits due to performance).
            // And, since retrieving just the hits for one group couldn't be done (findHitsFromOnlyRequestedGroup == null), we need to unfortunately get all hits.
            SearchHitGroupsFromHits searchGroups = (SearchHitGroupsFromHits) params.hitsSample().groupWithStoredHits(hitGroups.groupCriteria(), Results.NO_LIMIT);
            // now run the separate grouping search, making sure not to actually store the hits.
            // Sorting of the resultant groups is not applied, but is also not required because the groups aren't shown, only their contents.
            // If a later query requests the groups in a sorted order, the cache will ensure these results become the input to that query anyway, so worst case we just deferred the work.
            jobHitGroups = searchGroups.executeAsync(); // place groups with hits in search cache
            hits = jobHitGroups
                .get() //get grouped results
                .get(viewGroupVal) // get group
                .storedResults(); // get results in group
        }

        // NOTE: sortBy is automatically applied to regular results, but not to results within groups
        // See ResultsGrouper::init (uses hits.getByOriginalOrder(i)) and DocResults::constructor
        // Also see SearchParams (hitsSortSettings, docSortSettings, hitGroupsSortSettings, docGroupsSortSettings)
        // There is probably no reason why we can't just sort/use the sort of the input results, but we need some more testing to see if everything is correct if we change this
        String sortBy = params.getSortProps().orElse(null);
        HitProperty sortProp = HitProperty.deserialize(hits, sortBy);
        if (sortProp != null)
            hits = hits.sort(sortProp);

        return Pair.of(jobHitGroups, hits);
    }
}

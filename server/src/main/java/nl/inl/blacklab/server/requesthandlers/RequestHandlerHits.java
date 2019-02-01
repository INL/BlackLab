package nl.inl.blacklab.server.requesthandlers;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.servlet.http.HttpServletRequest;

import org.apache.lucene.document.Document;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;

import nl.inl.blacklab.exceptions.RegexpTooLarge;
import nl.inl.blacklab.exceptions.WildcardTermTooBroad;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.QueryExplanation;
import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.TermFrequency;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
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
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.ContextSettings;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.search.BlsCacheEntry;

/**
 * Request handler for hit results.
 */
public class RequestHandlerHits extends RequestHandler {

    public RequestHandlerHits(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public int handle(DataStream ds) throws BlsException {
        Hits hits = null;
        Hits window = null;
        BlsCacheEntry<?> job = null;

        // Do we want to view a single group after grouping?
        String groupBy = searchParam.getString("group");
        if (groupBy == null)
            groupBy = "";
        String viewGroup = searchParam.getString("viewgroup");
        if (viewGroup == null)
            viewGroup = "";

        HitGroup group = null;
        ResultsStats hitsCount;
        ResultsStats docsCount;
        if (groupBy.length() > 0 && viewGroup.length() > 0) {
            
            // Viewing a single group in a grouped hits results
            
            // Group, then show hits from the specified group
            job = searchMan.searchNonBlocking(user, searchParam.hitsGrouped());
            HitGroups hitsGrouped;
            try {
                hitsGrouped = (HitGroups)job.get();
            } catch (InterruptedException | ExecutionException e) {
                throw RequestHandler.translateSearchException(e);
            }

            PropertyValue viewGroupVal = null;
            viewGroupVal = PropertyValue.deserialize(blIndex(), blIndex().mainAnnotatedField(), viewGroup);
            if (viewGroupVal == null)
                return Response.badRequest(ds, "ERROR_IN_GROUP_VALUE",
                        "Cannot deserialize group value: " + viewGroup);

            group = hitsGrouped.get(viewGroupVal);
            if (group == null)
                return Response.badRequest(ds, "GROUP_NOT_FOUND", "Group not found: " + viewGroup);

            // NOTE: sortBy is automatically applied to regular results, but not to results within groups
            // See ResultsGrouper::init (uses hits.getByOriginalOrder(i)) and DocResults::constructor
            // Also see SearchParams (hitsSortSettings, docSortSettings, hitGroupsSortSettings, docGroupsSortSettings)
            // There is probably no reason why we can't just sort/use the sort of the input results, but we need some more testing to see if everything is correct if we change this
            String sortBy = searchParam.getString("sort");
            HitProperty sortProp = (sortBy != null && !sortBy.isEmpty())
                    ? HitProperty.deserialize(group.storedResults(), sortBy)
                    : null;
            Hits hitsInGroup = sortProp != null ? group.storedResults().sort(sortProp) : group.storedResults();

            // Important, only count hits within this group for the total
            // We should have retrieved all the hits already, as JobGroups always counts all hits.
            hits = hitsInGroup;

            int first = Math.max(0, searchParam.getInteger("first"));
            int size = Math.min(Math.max(0, searchParam.getInteger("number")), searchMan.config().getParameters().getPageSize().getDefaultValue());
            if (!hitsInGroup.hitsStats().processedAtLeast(first))
                return Response.badRequest(ds, "HIT_NUMBER_OUT_OF_RANGE", "Non-existent hit number specified.");
            window = hitsInGroup.window(first, size);
            
            hitsCount = hitsInGroup.hitsStats();
            docsCount = hitsInGroup.docsStats();
            
        } else {
            
            // Regular hits search
            
            // Since we're going to always launch a totals count anyway, just do it right away
            // then construct a window on top of the total
            hits = searchMan.search(user, searchParam.hitsSample());
            job = searchMan.searchNonBlocking(user, searchParam.hitsCount()); // always launch totals nonblocking!
            docsCount = searchMan.search(user, searchParam.docsCount());
            try {
                hitsCount = (ResultCount)job.get();
            } catch (InterruptedException | ExecutionException e) {
                throw RequestHandler.translateSearchException(e);
            }
            if (searchParam.getBoolean("waitfortotal")) {
                // Wait until all hits have been counted.
                hitsCount.countedTotal();
            }
            
//            int sleepTime = 10;
//            int totalSleepTime = 0;

            // check if we have the requested window available
            // NOTE: don't create the HitsWindow object yet, as it will attempt to resolve the hits immediately and block the thread until they've been found.
            // Instead, check with the Hits object directly, instead of blindly getting (and thus loading) the hits by creating a window
//            int first = Math.max(0, searchParam.getInteger("first"));
//            int size = Math.min(Math.max(0, searchParam.getInteger("number")), searchMan.config().maxPageSize());

            window = searchMan.search(user, searchParam.hitsWindow());

            
//            hits.hitsStats().processedAtLeast(first + size);
//
//            // We blocked, so if we don't have the page available, the request is out of bounds.
//            if (hits.hitsStats().processedSoFar() < first)
//                first = 0;
//
//            window = hits.window(first, size);
        }

        if (searchParam.getString("calc").equals("colloc")) {
            dataStreamCollocations(ds, hits);
            return HTTP_OK;
        }

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
        if (searchParam.getBoolean("explain")) {
            TextPattern tp = searchParam.getPattern();
            try {
                QueryExplanation explanation = index.explain(QueryInfo.create(index), tp, null);
                ds.startEntry("explanation").startMap()
                        .entry("originalQuery", explanation.originalQuery())
                        .entry("rewrittenQuery", explanation.rewrittenQuery())
                        .endMap().endEntry();
            } catch (RegexpTooLarge e) {
                throw new BadRequest("REGEXP_TOO_LARGE", "Regular expression too large.");
            } catch (WildcardTermTooBroad e) {
                throw BlsException.wildcardTermTooBroad(e);
            }
        }
        ds.endMap().endEntry();

        ds.startEntry("hits").startList();
        Map<Integer, String> pids = new HashMap<>();
        ContextSettings contextSettings = searchParam.getContextSettings();
        Concordances concordances = null;
        Kwics kwics = null;
        if (contextSettings.concType() == ConcordanceType.CONTENT_STORE)
            concordances = window.concordances(contextSettings.size(), ConcordanceType.CONTENT_STORE);
        else
            kwics = window.kwics(contextSettings.size());
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
                    ds.startItem("group").startMap();
                    ds.entry("name", capturedGroup.getKey());
                    ds.entry("start", capturedGroup.getValue().start());
                    ds.entry("end", capturedGroup.getValue().end());
                    ds.endMap().endItem();
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
                ds.startEntry("left").contextList(c.annotations(), c.left()).endEntry()
                        .startEntry("match").contextList(c.annotations(), c.match()).endEntry()
                        .startEntry("right").contextList(c.annotations(), c.right()).endEntry();
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
                dataStreamDocumentInfo(ds, index, doc);
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
        TermFrequencyList tfl = originalHits.collocations(originalHits.field().mainAnnotation(), contextSize, sensitivity);
        for (TermFrequency tf : tfl) {
            ds.attrEntry("token", "text", tf.term, tf.frequency);
        }
        ds.endMap().endEntry().endMap();
    }

}

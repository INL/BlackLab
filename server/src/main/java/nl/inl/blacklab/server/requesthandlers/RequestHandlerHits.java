package nl.inl.blacklab.server.requesthandlers;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.lucene.document.Document;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;

import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.DocPropertyComplexFieldLength;
import nl.inl.blacklab.resultproperty.HitPropValue;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.QueryExplanation;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.TermFrequency;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.nint.IndexMetadata;
import nl.inl.blacklab.search.results.DocOrHitGroups;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.HitGroup;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.HitsWindow;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.ServiceUnavailable;
import nl.inl.blacklab.server.jobs.Job;
import nl.inl.blacklab.server.jobs.JobHitsGrouped;
import nl.inl.blacklab.server.jobs.JobHitsTotal;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.search.BlsConfig;

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
        if (BlsConfig.traceRequestHandling)
            logger.debug("RequestHandlerHits.handle start");

        Hits total = null;
        HitsWindow window = null;
        Job job = null;

        // Do we want to view a single group after grouping?
        String groupBy = searchParam.getString("group");
        if (groupBy == null)
            groupBy = "";
        String viewGroup = searchParam.getString("viewgroup");
        if (viewGroup == null)
            viewGroup = "";

        try {
            HitGroup group = null;
            boolean block = isBlockingOperation();
            if (groupBy.length() > 0 && viewGroup.length() > 0) {
                // Yes. Group, then show hits from the specified group
                job = searchMan.search(user, searchParam.hitsGrouped(), block);
                JobHitsGrouped jobGrouped = (JobHitsGrouped) job;

                // If search is not done yet, indicate this to the user
                if (!jobGrouped.finished()) {
                    return Response.busy(ds, servlet);
                }

                HitPropValue viewGroupVal = null;
                viewGroupVal = HitPropValue.deserialize(jobGrouped.getHits(), viewGroup);
                if (viewGroupVal == null)
                    return Response.badRequest(ds, "ERROR_IN_GROUP_VALUE",
                            "Cannot deserialize group value: " + viewGroup);

                group = jobGrouped.getGroups().getGroup(viewGroupVal);
                if (group == null)
                    return Response.badRequest(ds, "GROUP_NOT_FOUND", "Group not found: " + viewGroup);

                // NOTE: sortBy is automatically applied to regular results, but not to results within groups
                // See ResultsGrouper::init (uses hits.getByOriginalOrder(i)) and DocResults::constructor
                // Also see SearchParams (hitsSortSettings, docSortSettings, hitGroupsSortSettings, docGroupsSortSettings)
                // There is probably no reason why we can't just sort/use the sort of the input results, but we need some more testing to see if everything is correct if we change this
                String sortBy = searchParam.getString("sort");
                HitProperty sortProp = (sortBy != null && !sortBy.isEmpty())
                        ? HitProperty.deserialize(group.getHits(), sortBy)
                        : null;
                Hits hitsInGroup = sortProp != null ? group.getHits().sortedBy(sortProp) : group.getHits();

                // Important, only count hits within this group for the total
                // We should have retrieved all the hits already, as JobGroups always counts all hits.
                total = hitsInGroup;

                int first = Math.max(0, searchParam.getInteger("first"));
                int size = Math.min(Math.max(0, searchParam.getInteger("number")), searchMan.config().maxPageSize());
                if (!hitsInGroup.sizeAtLeast(first))
                    return Response.badRequest(ds, "HIT_NUMBER_OUT_OF_RANGE", "Non-existent hit number specified.");
                window = hitsInGroup.window(first, size);
            } else {
                // Since we're going to always launch a totals count anyway, just do it right away
                // then construct a window on top of the total
                job = searchMan.search(user, searchParam.hitsTotal(), false); // always launch totals nonblocking!
                JobHitsTotal jobTotal = (JobHitsTotal) job;

                int sleepTime = 10;
                int totalSleepTime = 0;
                while ((total = jobTotal.getHits()) == null) { // Wait for job to start up for a bit
                    try {
                        totalSleepTime += sleepTime;
                        Thread.sleep(sleepTime = Math.max(sleepTime * 2, 500));
                        if (totalSleepTime >= 5000)
                            throw new ServiceUnavailable("Timeout");
                    } catch (InterruptedException e) {
                        throw new ServiceUnavailable("Interrupted");
                    }
                }

                // check if we have the requested window available
                // NOTE: don't create the HitsWindow object yet, as it will attempt to resolve the hits immediately and block the thread until they've been found.
                // Instead, check with the Hits object directly, instead of blindly getting (and thus loading) the hits by creating a window
                int first = Math.max(0, searchParam.getInteger("first"));
                int size = Math.min(Math.max(0, searchParam.getInteger("number")), searchMan.config().maxPageSize());

                total.sizeAtLeast(first + size);

                // We blocked, so if we don't have the page available, the request is out of bounds.
                if (total.countSoFarHitsRetrieved() < first)
                    first = 0;

                window = total.window(first, size);
            }

            if (searchParam.getString("calc").equals("colloc")) {
                dataStreamCollocations(ds, window.getOriginalHits());
                return HTTP_OK;
            }

            DocResults perDocResults = null;

            Searcher searcher = total.getSearcher();

            boolean includeTokenCount = searchParam.getBoolean("includetokencount");
            int totalTokens = -1;
            IndexMetadata indexMetadata = searcher.getIndexMetadata();
            if (includeTokenCount) {
                perDocResults = window.getOriginalHits().perDocResults();
                // Determine total number of tokens in result set
                String fieldName = indexMetadata.annotatedFields().main().name();
                DocProperty propTokens = new DocPropertyComplexFieldLength(fieldName);
                totalTokens = perDocResults.intSum(propTokens);
            }

            // Search is done; construct the results object

            ds.startMap();

            // The summary
            ds.startEntry("summary").startMap();

            double totalTime = job.threwException() ? -1 : job.userWaitTime();

            // TODO timing is now broken because we always retrieve total and use a window on top of it,
            // so we can no longer differentiate the total time from the time to retrieve the requested window
            addSummaryCommonFields(ds, searchParam, job.userWaitTime(), totalTime, window, total, false,
                    (DocResults) null, (DocOrHitGroups) null, window);
            if (includeTokenCount)
                ds.entry("tokensInMatchingDocuments", totalTokens);
            ds.startEntry("docFields");
            RequestHandler.dataStreamDocFields(ds, searcher.getIndexMetadata());
            ds.endEntry();
            if (searchParam.getBoolean("explain")) {
                TextPattern tp = searchParam.getPattern();
                QueryExplanation explanation = searcher.explain(tp);
                ds.startEntry("explanation").startMap()
                        .entry("originalQuery", explanation.getOriginalQuery())
                        .entry("rewrittenQuery", explanation.getRewrittenQuery())
                        .endMap().endEntry();
            }
            ds.endMap().endEntry();

            ds.startEntry("hits").startList();
            Map<Integer, String> pids = new HashMap<>();
            for (Hit hit : window) {
                ds.startItem("hit").startMap();

                // Find pid
                String pid = pids.get(hit.doc);
                if (pid == null) {
                    Document document = searcher.document(hit.doc);
                    pid = getDocumentPid(searcher, hit.doc, document);
                    pids.put(hit.doc, pid);
                }

                boolean useOrigContent = searchParam.getString("usecontent").equals("orig");

                // TODO: use RequestHandlerDocSnippet.getHitOrFragmentInfo()

                // Add basic hit info
                ds.entry("docPid", pid);
                ds.entry("start", hit.start);
                ds.entry("end", hit.end);

                if (useOrigContent) {
                    // Add concordance from original XML
                    Concordance c = window.getConcordance(hit);
                    ds.startEntry("left").plain(c.left()).endEntry()
                            .startEntry("match").plain(c.match()).endEntry()
                            .startEntry("right").plain(c.right()).endEntry();
                } else {
                    // Add KWIC info
                    Kwic c = window.getKwic(hit);
                    ds.startEntry("left").contextList(c.getProperties(), c.getLeft()).endEntry()
                            .startEntry("match").contextList(c.getProperties(), c.getMatch()).endEntry()
                            .startEntry("right").contextList(c.getProperties(), c.getRight()).endEntry();
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
                String pid = pids.get(hit.doc);

                // Add document info if we didn't already
                if (!docsDone.contains(hit.doc)) {
                    docsDone.add(hit.doc);
                    ds.startAttrEntry("docInfo", "pid", pid);
                    if (!pid.equals(lastPid)) {
                        doc = searcher.document(hit.doc);
                        lastPid = pid;
                    }
                    dataStreamDocumentInfo(ds, searcher, doc);
                    ds.endAttrEntry();
                }
            }
            ds.endMap().endEntry();

            if (searchParam.hasFacets()) {
                // Now, group the docs according to the requested facets.
                if (perDocResults == null)
                    perDocResults = window.getOriginalHits().perDocResults();
                ds.startEntry("facets");
                dataStreamFacets(ds, perDocResults, searchParam.facets());
                ds.endEntry();
            }

            ds.endMap();

            if (BlsConfig.traceRequestHandling)
                logger.debug("RequestHandlerHits.handle end");
            return HTTP_OK;
        } finally {
            if (job != null)
                job.decrRef();
        }
    }

    private void dataStreamCollocations(DataStream ds, Hits originalHits) {
        originalHits.settings().setContextSize(searchParam.getInteger("wordsaroundhit"));
        ds.startMap().startEntry("tokenFrequencies").startMap();
        TermFrequencyList tfl = originalHits.getCollocations();
        tfl.sort();
        for (TermFrequency tf : tfl) {
            ds.attrEntry("token", "text", tf.term, tf.frequency);
        }
        ds.endMap().endEntry().endMap();
    }

}

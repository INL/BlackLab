package nl.inl.blacklab.server.requesthandlers;

import java.util.concurrent.ExecutionException;

import javax.servlet.http.HttpServletRequest;

import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.HitGroup;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.ResultCount;
import nl.inl.blacklab.search.results.WindowStats;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.config.DefaultMax;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.jobs.WindowSettings;
import nl.inl.blacklab.server.search.BlsCacheEntry;

/**
 * Request handler for grouped hit results.
 */
public class RequestHandlerHitsGrouped extends RequestHandler {
    
    private static boolean INCLUDE_RELATIVE_FREQ = true; 

    public RequestHandlerHitsGrouped(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }
    
    @Override
    public int handle(DataStream ds) throws BlsException {
        // Get the window we're interested in
        BlsCacheEntry<HitGroups> search = searchMan.searchNonBlocking(user, searchParam.hitsGrouped());
        
        // Search is done; construct the results object
        HitGroups groups;
        try {
            groups = search.get();
        } catch (InterruptedException | ExecutionException e) {
            throw RequestHandler.translateSearchException(e);
        }

        ds.startMap();
        ds.startEntry("summary").startMap();
        WindowSettings windowSettings = searchParam.getWindowSettings();
        final int first = windowSettings.first() < 0 ? 0 : windowSettings.first();
        DefaultMax pageSize = searchMan.config().getParameters().getPageSize();
        final int requestedWindowSize = windowSettings.size() < 0
                || windowSettings.size() > pageSize.getMax() ? pageSize.getDefaultValue()
                        : windowSettings.size();
        int totalResults = groups.size();
        final int actualWindowSize = first + requestedWindowSize > totalResults ? totalResults - first
                : requestedWindowSize;
        WindowStats ourWindow = new WindowStats(first + requestedWindowSize < totalResults, first, requestedWindowSize, actualWindowSize);
        addSummaryCommonFields(ds, searchParam, search.timeUserWaited(), 0, groups, ourWindow);
        ResultCount hitsStats = searchMan.search(user, searchParam.hitsCount());
        ResultCount docsStats = searchMan.search(user, searchParam.docsCount());
        addNumberOfResultsSummaryTotalHits(ds, hitsStats, docsStats, false);
        ds.endMap().endEntry();

        searchLogger.setResultsFound(groups.size());
        
        // The list of groups found
        ds.startEntry("hitGroups").startList();
        DocProperty metadataGroupProperties = null;
//        DocGroups subcorpusGrouped = null;
        long tokensInSubcorpus = -1;
        if (INCLUDE_RELATIVE_FREQ) {
            logger.debug("## Init relative frequencies: get doc props");
            metadataGroupProperties = groups.groupCriteria().docPropsOnly();
            logger.debug("## Init relative frequencies: determine subcorpus");
            DocResults subcorpus = searchMan.search(user, searchParam.subcorpus());
            if (metadataGroupProperties != null) {
                // We're grouping on metadata. We need to know the subcorpus per group.
//                logger.debug("## Grouping on metadata, find subcorpora per group");
//                subcorpusGrouped = subcorpus.group(metadataGroupProperties, -1);
//                logger.debug("## (found " + subcorpusGrouped.size() + " groups)");
            } else {
                // We're not grouping on metadata. We only need to know the total subcorpus size.
                logger.debug("## NOT grouping on metadata, count tokens in total subcorpus");
                tokensInSubcorpus = subcorpus.tokensInMatchingDocs();
                logger.debug("## (tokens in total subcorpus: " + tokensInSubcorpus + ")");
            }
            logger.debug("## Done init relative frequencies");
        }
        int i = 0;
        for (HitGroup group : groups) {
            if (i >= first && i < first + requestedWindowSize) {
                logger.debug("## Group number " + i);
                
                if (INCLUDE_RELATIVE_FREQ && metadataGroupProperties != null) {
                    // Find size of corresponding subcorpus group
                    PropertyValue docPropValues = groups.groupCriteria().docPropValues(group.identity());
                    //DocGroup groupSubcorpus = subcorpusGrouped.get(docPropValues);
                    //tokensInSubcorpus = groupSubcorpus.storedResults().tokensInMatchingDocs();
                    tokensInSubcorpus = findSubcorpusSize(metadataGroupProperties, docPropValues);
                    logger.debug("## tokens in subcorpus group: " + tokensInSubcorpus);
                }
                
                // Calculate relative group size
                if (tokensInSubcorpus == 0)
                    tokensInSubcorpus = 1; // prevent division by zero...
                double relativeFrequency = (double)group.size() / tokensInSubcorpus;
                
                ds.startItem("hitgroup").startMap();
                ds.entry("identity", group.identity().serialize())
                        .entry("identityDisplay", group.identity().toString())
                        .entry("size", group.size());
                if (INCLUDE_RELATIVE_FREQ)
                    ds.entry("relativeFrequency", relativeFrequency);
                else
                    ds.entry("relativeFrequency", 0.1);
                ds.endMap().endItem();
            }
            i++;
        }
        ds.endList().endEntry();
        ds.endMap();

        return HTTP_OK;
    }

    private long findSubcorpusSize(DocProperty property, PropertyValue value) {
        // Construct a query that matches this propery value
        Query query = property.query(value); // analyzer....!
        if (query == null) {
            query = new MatchAllDocsQuery();
        }
        // Determine number of tokens in this subcorpus
        return searchParam.blIndex().queryDocuments(query).tokensInMatchingDocs();
    }

}

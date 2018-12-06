package nl.inl.blacklab.server.requesthandlers;

import java.util.concurrent.ExecutionException;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.results.DocGroup;
import nl.inl.blacklab.search.results.DocGroups;
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
        int i = 0;
        DocResults subcorpus = searchMan.search(user, searchParam.subcorpus());
        DocProperty metadataGroupProperties = groups.groupCriteria().docPropsOnly();
        DocGroups subcorpusGrouped = null;
        long tokensInSubcorpus = 0;
        if (metadataGroupProperties != null) {
            // We're grouping on metadata. We need to know the subcorpus per group. 
            subcorpusGrouped = subcorpus.group(metadataGroupProperties, -1);
        } else {
            // We're not grouping on metadata. We only need to know the total subcorpus size.
            tokensInSubcorpus = subcorpus.tokensInMatchingDocs();
        }
        for (HitGroup group : groups) {
            if (i >= first && i < first + requestedWindowSize) {
                
                if (metadataGroupProperties != null) {
                    // Find size of corresponding subcorpus group
                    PropertyValue docPropValues = groups.groupCriteria().docPropValues(group.identity());
                    DocGroup groupSubcorpus = subcorpusGrouped.get(docPropValues);
                    tokensInSubcorpus = groupSubcorpus.storedResults().tokensInMatchingDocs();
                }
                
                // Calculate relative group size
                double relativeFrequency = (double)group.size() / tokensInSubcorpus;
                
                ds.startItem("hitgroup").startMap();
                ds.entry("identity", group.identity().serialize())
                        .entry("identityDisplay", group.identity().toString())
                        .entry("size", group.size())
                        .entry("relativeFrequency", relativeFrequency);
                ds.endMap().endItem();
            }
            i++;
        }
        ds.endList().endEntry();
        ds.endMap();

        return HTTP_OK;
    }

}

package nl.inl.blacklab.server.requesthandlers;

import java.util.concurrent.ExecutionException;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.search.results.HitGroup;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.ResultCount;
import nl.inl.blacklab.search.results.WindowStats;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.jobs.WindowSettings;
import nl.inl.blacklab.server.search.NewBlsCacheEntry;

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
        NewBlsCacheEntry<HitGroups> search = searchMan.searchNonBlocking(user, searchParam.hitsGrouped());
        
        // Search is done; construct the results object
        HitGroups groups;
        try {
            groups = search.get();
        } catch (InterruptedException e) {
            throw new InterruptedSearch(e);
        } catch (ExecutionException e) {
            throw new BadRequest("INVALID_QUERY", "Invalid query: " + e.getCause().getMessage());
        }

        ds.startMap();
        ds.startEntry("summary").startMap();
        WindowSettings windowSettings = searchParam.getWindowSettings();
        final int first = windowSettings.first() < 0 ? 0 : windowSettings.first();
        final int requestedWindowSize = windowSettings.size() < 0
                || windowSettings.size() > searchMan.config().maxPageSize() ? searchMan.config().defaultPageSize()
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

        // The list of groups found
        ds.startEntry("hitGroups").startList();
        int i = 0;
        for (HitGroup group : groups) {
            if (i >= first && i < first + requestedWindowSize) {
                ds.startItem("hitgroup").startMap();
                ds.entry("identity", group.identity().serialize())
                        .entry("identityDisplay", group.identity().toString())
                        .entry("size", group.size());
                ds.endMap().endItem();
            }
            i++;
        }
        ds.endList().endEntry();
        ds.endMap();

        return HTTP_OK;
    }

}

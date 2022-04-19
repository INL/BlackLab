package nl.inl.blacklab.server.requesthandlers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.servlet.http.HttpServletRequest;

import org.apache.lucene.document.Document;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyMultiple;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.HitGroup;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.ResultsStats;
import nl.inl.blacklab.search.results.WindowStats;
import nl.inl.blacklab.searches.SearchCacheEntry;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.config.DefaultMax;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.jobs.WindowSettings;
import nl.inl.util.BlockTimer;

/**
 * Request handler for grouped hit results.
 */
public class RequestHandlerHitsGrouped extends RequestHandler {

    public static final boolean INCLUDE_RELATIVE_FREQ = true;

    public RequestHandlerHitsGrouped(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public int handle(DataStream ds) throws BlsException, InvalidQuery {
        HitGroups groups;
        SearchCacheEntry<HitGroups> search;
        try (BlockTimer ignored = BlockTimer.create("Searching hit groups")) {
            // Get the window we're interested in
            search = searchParam.hitsGroupedStats().executeAsync();
            // Search is done; construct the results object
            groups = search.get();
        } catch (InterruptedException | ExecutionException e) {
            throw RequestHandler.translateSearchException(e);
        }

        ds.startMap();
        ds.startEntry("summary").startMap();
        WindowSettings windowSettings = searchParam.getWindowSettings();
        final long first = Math.max(windowSettings.first(), 0);
        DefaultMax pageSize = searchMan.config().getParameters().getPageSize();
        final long requestedWindowSize = windowSettings.size() < 0
                || windowSettings.size() > pageSize.getMax() ? pageSize.getDefaultValue()
                        : windowSettings.size();
        long totalResults = groups.size();
        final long actualWindowSize = first + requestedWindowSize > totalResults ? totalResults - first
                : requestedWindowSize;
        WindowStats ourWindow = new WindowStats(first + requestedWindowSize < totalResults, first, requestedWindowSize, actualWindowSize);
        datastreamSummaryCommonFields(ds, searchParam, search.timeUserWaitedMs(), 0, groups, ourWindow);
        ResultsStats hitsStats = groups.hitsStats();
        ResultsStats docsStats = groups.docsStats();
        if (docsStats == null)
            docsStats = searchParam.docsCount().execute();

        // The list of groups found
        DocProperty metadataGroupProperties = null;
        DocResults subcorpus = null;
        CorpusSize subcorpusSize = null;
        if (INCLUDE_RELATIVE_FREQ) {
            metadataGroupProperties = groups.groupCriteria().docPropsOnly();
            subcorpus = searchParam.subcorpus().execute();
            subcorpusSize = subcorpus.subcorpusSize();
        }

        datastreamNumberOfResultsSummaryTotalHits(ds, hitsStats, docsStats, true, false, subcorpusSize);
        ds.endMap().endEntry();

        /* Gather group values per property:
         * In the case we're grouping by multiple values, the DocPropertyMultiple and PropertyValueMultiple will
         * contain the sub properties and values in the same order.
         */
        boolean isMultiValueGroup = groups.groupCriteria() instanceof HitPropertyMultiple;
        List<HitProperty> prop = isMultiValueGroup ? groups.groupCriteria().props() : List.of(groups.groupCriteria());

        ds.startEntry("hitGroups").startList();
        long last = Math.min(first + requestedWindowSize, groups.size());

        Map<Integer, Document> luceneDocs = new HashMap<>();
        try (BlockTimer ignored = BlockTimer.create("Serializing groups to JSON")) {
            for (long i = first; i < last; ++i) {
                HitGroup group = groups.get(i);
                PropertyValue id = group.identity();
                List<PropertyValue> valuesForGroup = isMultiValueGroup ? id.values() : List.of(id);

                if (INCLUDE_RELATIVE_FREQ && metadataGroupProperties != null) {
                    // Find size of corresponding subcorpus group
                    PropertyValue docPropValues = groups.groupCriteria().docPropValues(id);
                    subcorpusSize = findSubcorpusSize(searchParam, subcorpus.query(), metadataGroupProperties, docPropValues);
//                    logger.debug("## tokens in subcorpus group: " + subcorpusSize.getTokens());
                }

                long numberOfDocsInGroup = group.storedResults().docsStats().countedTotal();

                ds.startItem("hitgroup").startMap();
                ds
                .entry("identity", id.serialize())
                .entry("identityDisplay", id.toString())
                .entry("size", group.size());

                ds.startEntry("properties").startList();
                for (int j = 0; j < prop.size(); ++j) {
                    final HitProperty hp = prop.get(j);
                    final PropertyValue pv = valuesForGroup.get(j);

                    ds.startItem("property").startMap();
                    ds.entry("name", hp.serialize());
                    ds.entry("value", pv.toString());
                    ds.endMap().endItem();
                }
                ds.endList().endEntry();

                if (INCLUDE_RELATIVE_FREQ) {
                    ds.entry("numberOfDocs", numberOfDocsInGroup);
                    if (metadataGroupProperties != null) {
                        datastreamSubcorpusSize(ds, subcorpusSize);
                    }
                }

                if (searchParam.includeGroupContents()) {
                    Hits hitsInGroup = group.storedResults();
                    datastreamHits(ds, hitsInGroup, luceneDocs, searchParam.getContextSettings());
                }

                ds.endMap().endItem();
            }
        }
        ds.endList().endEntry();

        if (searchParam.includeGroupContents()) {
            datastreamDocInfos(ds, blIndex(), luceneDocs, getMetadataToWrite());
        }
        ds.endMap();

        return HTTP_OK;
    }

    static CorpusSize findSubcorpusSize(SearchParameters searchParam, Query metadataFilterQuery, DocProperty property, PropertyValue value) {
        if (!property.canConstructQuery(searchParam.blIndex(), value))
            return CorpusSize.EMPTY; // cannot determine subcorpus size of empty value
        // Construct a query that matches this propery value
        Query query = property.query(searchParam.blIndex(), value); // analyzer....!
        if (query == null) {
            query = metadataFilterQuery;
        } else {
            // Combine with subcorpus query
            Builder builder = new BooleanQuery.Builder();
            builder.add(metadataFilterQuery, Occur.MUST);
            builder.add(query, Occur.MUST);
            query = builder.build();
        }
        // Determine number of tokens in this subcorpus
        return searchParam.blIndex().queryDocuments(query).subcorpusSize(true);
    }
}
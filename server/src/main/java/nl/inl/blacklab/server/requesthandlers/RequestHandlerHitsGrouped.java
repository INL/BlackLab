package nl.inl.blacklab.server.requesthandlers;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.servlet.http.HttpServletRequest;

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.Query;

import io.dropwizard.metrics5.MetricRegistry;
import io.dropwizard.metrics5.Timer;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyMultiple;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.PropertyValueMultiple;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.results.CorpusSize;
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

    private final Timer searchTimer = BlackLab.metrics.timer(MetricRegistry.name(RequestHandlerHitsGrouped.class, "search"));
    private final Timer jsonTimer = BlackLab.metrics.timer(MetricRegistry.name(RequestHandlerHitsGrouped.class, "json"));

    public static final boolean INCLUDE_RELATIVE_FREQ = true;

    public RequestHandlerHitsGrouped(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public int handle(DataStream ds) throws BlsException {
        HitGroups groups;
        BlsCacheEntry<HitGroups> search;
        try(Timer.Context context = searchTimer.time()) {
            // Get the window we're interested in
            search = searchMan.searchNonBlocking(user, searchParam.hitsGrouped());
            // Search is done; construct the results object
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

        // The list of groups found
        DocProperty metadataGroupProperties = null;
        DocResults subcorpus = null;
        CorpusSize subcorpusSize = null;
        if (INCLUDE_RELATIVE_FREQ) {
            metadataGroupProperties = groups.groupCriteria().docPropsOnly();
            subcorpus = searchMan.search(user, searchParam.subcorpus());
            subcorpusSize = subcorpus.subcorpusSize();
        }

        addNumberOfResultsSummaryTotalHits(ds, hitsStats, docsStats, false, subcorpusSize);
        ds.endMap().endEntry();

        searchLogger.setResultsFound(groups.size());

        /* Gather group values per property:
         * Documents are grouped by one or more "properties", every group represents hits with the same "values" for these properties.
         * This means there are two structures:
         * a Map<values, group> that was used to gather the hits.
         * an Array<group> governing the ordering of those groups.
         *
         * A result of this is that we iterate the array (in order to retrieve groups in the requested/correct order),
         * but the group itself doesn't know the values that it represents.
         * We need to invert the map so we can find the values from the group.
         *
         * In the case we're grouping by multiple values, the DocPropertyMultiple and PropertyValueMultiple will
         * contain the sub properties and values in the same order.
         */
        boolean isMultiValueGroup = groups.groupCriteria() instanceof HitPropertyMultiple;
        List<HitProperty> prop = isMultiValueGroup ? ((HitPropertyMultiple) groups.groupCriteria()).props() : Arrays.asList(groups.groupCriteria());

        ds.startEntry("hitGroups").startList();
        int last = Math.min(first + requestedWindowSize, groups.size());

        try (Timer.Context c = jsonTimer.time()) {
            for (int i = first; i < last; ++i) {
                HitGroup group = groups.get(i);
                PropertyValue id = group.identity();
                List<PropertyValue> valuesForGroup = isMultiValueGroup ? ((PropertyValueMultiple) id).values() : Arrays.asList(id);

                if (INCLUDE_RELATIVE_FREQ && metadataGroupProperties != null) {
                    // Find size of corresponding subcorpus group
                    PropertyValue docPropValues = groups.groupCriteria().docPropValues(group.identity());
                    subcorpusSize = findSubcorpusSize(searchParam, subcorpus.query(), metadataGroupProperties, docPropValues, true);
                    logger.debug("## tokens in subcorpus group: " + subcorpusSize.getTokens());
                }

                int numberOfDocsInGroup = group.storedResults().docsStats().countedTotal();

                ds.startItem("hitgroup").startMap();
                ds
                .entry("identity", group.identity().serialize())
                .entry("identityDisplay", group.identity().toString())
                .entry("size", group.size());

                ds.startEntry("values").startMap();
                for (int j = 0; j < prop.size(); ++j) {
                    final HitProperty hp = prop.get(j);
                    final PropertyValue pv = valuesForGroup.get(j);
                    ds.entry(hp.serialize(), pv.toString());
                }
                ds.endMap().endEntry();

                if (INCLUDE_RELATIVE_FREQ) {
                    ds.entry("numberOfDocs", numberOfDocsInGroup);
                    if (metadataGroupProperties != null) {
                        addSubcorpusSize(ds, subcorpusSize);
                    }
                }
                ds.endMap().endItem();

            }
        }
        ds.endList().endEntry();
        ds.endMap();

        return HTTP_OK;
    }

    static CorpusSize findSubcorpusSize(SearchParameters searchParam, Query metadataFilterQuery, DocProperty property, PropertyValue value, boolean countTokens) {
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
        return searchParam.blIndex().queryDocuments(query).subcorpusSize(countTokens);
    }
}
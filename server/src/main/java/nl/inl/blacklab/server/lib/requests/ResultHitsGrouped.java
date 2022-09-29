package nl.inl.blacklab.server.lib.requests;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyMultiple;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.HitGroup;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.ResultsStats;
import nl.inl.blacklab.search.results.WindowStats;
import nl.inl.blacklab.searches.SearchCacheEntry;
import nl.inl.blacklab.server.config.DefaultMax;
import nl.inl.blacklab.server.jobs.WindowSettings;
import nl.inl.blacklab.server.lib.SearchCreator;
import nl.inl.blacklab.server.lib.SearchTimings;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.util.BlockTimer;

public class ResultHitsGrouped {

    private final HitGroups groups;

    private final WindowStats window;

    private final SearchTimings timings;

    private final ResultsStats hitsStats;

    private ResultsStats docsStats;

    private final DocProperty metadataGroupProperties;

    private final CorpusSize subcorpusSize;

    private final List<ResultHitGroup> groupInfos;

    private Map<String, ResultDocInfo> docInfos;

    public static ResultHitsGrouped get(SearchCreator params, SearchManager searchMan) throws InvalidQuery {
        return new ResultHitsGrouped(params, searchMan);
    }

    public ResultHitsGrouped(SearchCreator params, SearchManager searchMan) throws InvalidQuery {
        SearchCacheEntry<HitGroups> search;
        try (BlockTimer ignored = BlockTimer.create("Searching hit groups")) {
            // Get the window we're interested in
            search = params.hitsGroupedStats().executeAsync();
            // Search is done; construct the results object
            groups = search.get();
        } catch (InterruptedException | ExecutionException e) {
            throw WebserviceOperations.translateSearchException(e);
        }

        WindowSettings windowSettings = params.windowSettings();
        final long first = Math.max(windowSettings.first(), 0);
        DefaultMax pageSize = searchMan.config().getParameters().getPageSize();
        final long requestedWindowSize = windowSettings.size() < 0
                || windowSettings.size() > pageSize.getMax() ? pageSize.getDefaultValue()
                : windowSettings.size();
        long totalResults = groups.size();
        final long actualWindowSize = first + requestedWindowSize > totalResults ? totalResults - first
                : requestedWindowSize;
        window = new WindowStats(first + requestedWindowSize < totalResults, first, requestedWindowSize,
                actualWindowSize);
        timings = new SearchTimings(search.timer().time(), 0);

        hitsStats = groups.hitsStats();
        docsStats = groups.docsStats();
        if (docsStats == null)
            docsStats = params.docsCount().execute();

        // The list of groups found
        metadataGroupProperties = groups.groupCriteria().docPropsOnly();
        DocResults subcorpus = params.subcorpus().execute();
        subcorpusSize = subcorpus.subcorpusSize();

        /* Gather group values per property:
         * In the case we're grouping by multiple values, the DocPropertyMultiple and PropertyValueMultiple will
         * contain the sub properties and values in the same order.
         */
        boolean isMultiValueGroup = groups.groupCriteria() instanceof HitPropertyMultiple;
        List<HitProperty> prop = isMultiValueGroup ? groups.groupCriteria().props() : List.of(groups.groupCriteria());

        long last = Math.min(first + requestedWindowSize, groups.size());

        Map<Integer, Document> luceneDocs = new HashMap<>();
        groupInfos = new ArrayList<>();
        try (BlockTimer ignored = BlockTimer.create("Serializing groups to JSON")) {
            for (long i = first; i < last; ++i) {
                HitGroup group = groups.get(i);
                groupInfos.add(new ResultHitGroup(params, groups, group, metadataGroupProperties,
                        subcorpus, isMultiValueGroup, prop, luceneDocs));
            }
        }

        docInfos = null;
        if (params.includeGroupContents()) {
            BlackLabIndex index = params.blIndex();
            Collection<MetadataField> metadataToWrite = WebserviceOperations.getMetadataToWrite(params);
            docInfos = WebserviceOperations.getDocInfos(index, luceneDocs, metadataToWrite);
        }
    }

    public HitGroups getGroups() {
        return groups;
    }

    public WindowStats getWindow() {
        return window;
    }

    public SearchTimings getTimings() {
        return timings;
    }

    public ResultsStats getHitsStats() {
        return hitsStats;
    }

    public ResultsStats getDocsStats() {
        return docsStats;
    }

    public DocProperty getMetadataGroupProperties() {
        return metadataGroupProperties;
    }

    public CorpusSize getSubcorpusSize() {
        return subcorpusSize;
    }

    public List<ResultHitGroup> getGroupInfos() {
        return groupInfos;
    }

    public Map<String, ResultDocInfo> getDocInfos() {
        return docInfos;
    }
}

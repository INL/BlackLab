/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.search.Query;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;

import nl.inl.blacklab.forwardindex.FiidLookup;
import nl.inl.blacklab.resultproperty.GroupProperty;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.results.Hits.HitsArrays;

/**
 * Groups results on the basis of a list of criteria.
 *
 * This class allows random access to the groups, and each group provides random
 * access to the hits. Note that this means that all hits found must be
 * retrieved, which may be infeasible for large results sets.
 */
public class HitGroups extends ResultsList<HitGroup, GroupProperty<Hit, HitGroup>> implements ResultGroups<Hit> {

    /**
     * Construct HitGroups from a list of HitGroup instances.
     *
     * @param queryInfo query info
     * @param results list of groups
     * @param totalDocs total number of documents from which Results were created, pass null if unknown (when results have been filtered/windowed/sampled).
     * @param groupCriteria what hits would be grouped by
     * @param sampleParameters how groups were sampled, or null if not applicable
     * @param windowStats what groups window this is, or null if not applicable
     * @return grouped hits
     */
    public static HitGroups fromList(QueryInfo queryInfo, List<HitGroup> results, HitProperty groupCriteria, SampleParameters sampleParameters, WindowStats windowStats, ResultsStats hitsStats, ResultsStats docsStats) {
        return new HitGroups(queryInfo, results, groupCriteria, sampleParameters, windowStats, hitsStats, docsStats);
    }

    /**
     * Construct HitGroups from a list of hits.
     *
     * @param hits hits to group
     * @param criteria criteria to group by
     * @param maxResultsToStorePerGroup max results to store
     * @return grouped hits
     */
    public static HitGroups fromHits(Hits hits, HitProperty criteria, int maxResultsToStorePerGroup) {
        return new HitGroups(hits, criteria, maxResultsToStorePerGroup);
    }

    public static HitGroups tokenFrequencies(QueryInfo queryInfo, Query filterQuery, SearchSettings searchSettings, HitProperty property, int maxHits) {
        return HitGroupsTokenFrequencies.get(queryInfo, filterQuery, searchSettings, property, maxHits);
    }

    private HitProperty criteria;

    /**
     * The groups.
     */
    private Map<PropertyValue, HitGroup> groups = new HashMap<>();

    /**
     * Total number of results in the source set of hits. 
     * Note that unlike other Hits instances (samples/sorts/windows), we should safely be able to copy these from our source, 
     * because hits are always fully read before constructing groups.
     */
    protected final ResultsStats hitsStats;
    /**
     * Total number of results in the source set of hits. 
     * Note that unlike other Hits instances (samples/sorts/windows), we should safely be able to copy these from our source, 
     * because hits are always fully read before constructing groups.
     */
    protected final ResultsStats docsStats;

    /**
     * Size of the largest group.
     */
    private int largestGroupSize = 0;

    private WindowStats windowStats = null;

    private SampleParameters sampleParameters = null;

    private int resultObjects;

    /**
     * Construct a ResultsGrouper object, by grouping the supplied hits.
     *
     * @param hits the hits to group
     * @param criteria the criteria to group on
     * @param maxResultsToStorePerGroup how many results to store per group at most
     */
    protected HitGroups(Hits hits, HitProperty criteria, int maxResultsToStorePerGroup) {
        super(hits.queryInfo());
        if (criteria == null)
            throw new IllegalArgumentException("Must have criteria to group on");
        this.criteria = criteria;

        List<Annotation> requiredContext = criteria.needsContext();
        List<FiidLookup> fiidLookups = FiidLookup.getList(requiredContext, hits.queryInfo().index().reader());
        criteria = criteria.copyWith(hits, requiredContext == null ? null : new Contexts(hits, requiredContext, criteria.needsContextSize(hits.index()), fiidLookups));
        
        //Thread currentThread = Thread.currentThread();
        Map<PropertyValue, HitsArrays> groupLists = new HashMap<>();
        Map<PropertyValue, Integer> groupSizes = new HashMap<>();
        resultObjects = 0;
        int i = 0;
        for (Hit hit: hits) {
            PropertyValue identity = criteria.get(i);
            HitsArrays group = groupLists.get(identity);
            if (group == null) {
                group = new HitsArrays();
                groupLists.put(identity, group);
            }
            if (maxResultsToStorePerGroup < 0 || group.size() < maxResultsToStorePerGroup) {
                group.add(hit);
                resultObjects++;
            }
            Integer groupSize = groupSizes.get(identity);
            if (groupSize == null)
                groupSize = 1;
            else
                groupSize++;
            if (groupSize > largestGroupSize)
                largestGroupSize = groupSize;
            groupSizes.put(identity, groupSize);
            ++i;
        }
        resultObjects += groupLists.size();
        for (Map.Entry<PropertyValue, HitsArrays> e : groupLists.entrySet()) {
            PropertyValue groupId = e.getKey();
            HitsArrays hitList = e.getValue();
            Integer groupSize = groupSizes.get(groupId);
            HitGroup group = HitGroup.fromList(queryInfo(), groupId, hitList, hits.capturedGroups(), groupSize);
            groups.put(groupId, group);
            results.add(group);
        }

        // Make a copy so we don't keep any references to the source hits
        this.hitsStats = hits.hitsStats().save();
        this.docsStats = hits.docsStats().save();
    }

    protected HitGroups(QueryInfo queryInfo, List<HitGroup> groups, HitProperty groupCriteria, SampleParameters sampleParameters, WindowStats windowStats, ResultsStats hitsStats, ResultsStats docsStats) {
        super(queryInfo);
        this.criteria = groupCriteria;
        this.windowStats = windowStats;
        this.sampleParameters = sampleParameters;
        resultObjects = 0;
        for (HitGroup group: groups) {
            if (group.size() > largestGroupSize)
                largestGroupSize = group.size();
            results.add(group);
            this.groups.put(group.identity(), group);
            resultObjects += group.numberOfStoredResults() + 1;
        }

        // Make a copy so we don't keep any references to the source hits
        this.hitsStats = hitsStats.save();
        this.docsStats = docsStats.save();
    }


    @Override
    public HitProperty groupCriteria() {
        return criteria;
    }

    @Override
    protected void ensureResultsRead(int number) {
        // NOP
    }

    @Override
    public HitGroups sort(GroupProperty<Hit, HitGroup> sortProp) {
        ensureAllResultsRead();
        List<HitGroup> sorted = new ArrayList<HitGroup>(this.results);
        sorted.sort(sortProp);
        // Sorted contains the same hits as us, so we can pass on our result statistics.
        return HitGroups.fromList(queryInfo(), sorted, criteria, (SampleParameters)null, (WindowStats)null, hitsStats, docsStats);
    }
    
    /**
     * Take a sample of hits by wrapping an existing Hits object.
     *
     * @param sampleParameters sample parameters
     * @return the sample
     */
    @Override
    public HitGroups sample(SampleParameters sampleParameters) {
        List<HitGroup> sample = Results.doSample(this, sampleParameters);
        Pair<ResultsStats, ResultsStats> stats = getStatsOfSample(sample, this.hitsStats.maxStats(), this.docsStats.maxStats());
        return HitGroups.fromList(queryInfo(), sample, groupCriteria(), sampleParameters, (WindowStats)null, stats.getLeft(), stats.getRight());
    }

    /**
     * Get the total number of hits
     *
     * @return the number of hits
     */
    @Override
    public int sumOfGroupSizes() {
        return hitsStats.countedTotal();
    }

    /**
     * Return the size of the largest group
     *
     * @return size of the largest group
     */
    @Override
    public int largestGroupSize() {
        return largestGroupSize;
    }

    @Override
    public String toString() {
        return "ResultsGrouper with " + size() + " groups";
    }

    @Override
    public HitGroup get(PropertyValue identity) {
        return groups.get(identity);
    }

    @Override
    public int size() {
        return groups.size();
    }

    @Override
    public WindowStats windowStats() {
        return windowStats;
    }

    @Override
    public SampleParameters sampleParameters() {
        return sampleParameters;
    }

    @Override
    public HitGroups window(int first, int number) {
        List<HitGroup> resultsWindow = Results.doWindow(this, first, number);
        boolean hasNext = resultsProcessedAtLeast(first + resultsWindow.size() + 1);
        WindowStats windowStats = new WindowStats(hasNext, first, number, resultsWindow.size());
        // Note: a window is just a subset of the total result set.
        // We shouldn't recalculate the totals, as windows are "transparent"
        return HitGroups.fromList(queryInfo(), resultsWindow, criteria, (SampleParameters)null, windowStats, this.hitsStats, this.docsStats); // copy actual totals. Window should be "transparent"
    }

    @Override
    public HitGroups filter(GroupProperty<Hit, HitGroup> property, PropertyValue value) {
        List<HitGroup> list = this.results.stream().filter(group -> property.get(group).equals(value)).collect(Collectors.toList()); 
        Pair<ResultsStats, ResultsStats> stats = getStatsOfSample(list, this.hitsStats.maxStats(), this.docsStats.maxStats());
        return HitGroups.fromList(queryInfo(), list, groupCriteria(), (SampleParameters)null, (WindowStats)null, stats.getLeft(), stats.getRight());
    }

    @Override
    public ResultGroups<HitGroup> group(GroupProperty<Hit, HitGroup> criteria, int maxResultsToStorePerGroup) {
        throw new UnsupportedOperationException("Cannot group HitGroups");
    }

    @Override
    public HitGroups withFewerStoredResults(int maximumNumberOfResultsPerGroup) {
        if (maximumNumberOfResultsPerGroup < 0)
            maximumNumberOfResultsPerGroup = Integer.MAX_VALUE;
        List<HitGroup> truncatedGroups = new ArrayList<>();
        for (HitGroup group: results) {
            HitGroup newGroup = HitGroup.fromHits(group.identity(), group.storedResults().window(0, maximumNumberOfResultsPerGroup), group.size());
            truncatedGroups.add(newGroup);
        }

        // Since we truncated hits, the number of "processed" (aka stored) hits has changed
        // So recalculate
        Pair<ResultsStats, ResultsStats> stats = getStatsOfSample(truncatedGroups, this.hitsStats.maxStats(), this.docsStats.maxStats());

        // Merge back in the count amounts, as those didn't change, but getStatsOfSample can't accurately get a docs count if not all hits are present any more
        // So we need to copy back in the original docs count.
        ResultsStats hitsStats = new ResultsStatsStatic(stats.getLeft().processedTotal(), this.hitsStats.countedTotal(), this.hitsStats.maxStats());
        ResultsStats docsStats = new ResultsStatsStatic(stats.getRight().processedTotal(), this.docsStats.countedTotal(), this.docsStats.maxStats());
        return HitGroups.fromList(queryInfo(), truncatedGroups, criteria, (SampleParameters)null, windowStats, hitsStats, docsStats);
    }

    @Override
    public boolean doneProcessingAndCounting() {
        return true;
    }

    @Override
    public Map<PropertyValue, HitGroup> getGroupMap() {
        return groups;
    }

    @Override
    public int numberOfResultObjects() {
        return resultObjects;
    }

    /** 
     * Get document stats for these groups.
     * NOTE: docsCounted will return -1 if this HitGroups instance is a sample and hasn't got all hits stored 
     * (it is impossible to count accurately in that case as one document may be in more than one group)
     * @return stats 
     */
    public ResultsStats docsStats() {
        return docsStats;
    }
    
    public ResultsStats hitsStats() {
        return hitsStats;
    }

    /**
     * Compute total number of hits & documents in the sample
     * NOTE: docsStats might return -1 for totalDocsCounted if not all hits are stored/retrieved
     *  
     * @param sample a sample of the full results set
     * @param maxHitsStatsOfSource copied from source of sample. Since if the source hit the limits, then it follows that the sample is also limited
     * @param maxDocsStatsOfSource copied from source of sample. Since if the source hit the limits, then it follows that the sample is also limited
     * @return hitsStats in left, docsStats in right
     */
    private static Pair<ResultsStats, ResultsStats> getStatsOfSample(List<HitGroup> sample, MaxStats maxHitsStatsOfSource, MaxStats maxDocsStatsOfSource) {
        int hitsCounted = 0;
        int hitsRetrieved = 0;
        int docsRetrieved = 0;
        
        IntHashSet docs = new IntHashSet();
        for (HitGroup h : sample) {
            hitsCounted += h.size();
            for (Hit hh : h.storedResults()) {
                ++hitsRetrieved;
                if (docs.add(hh.doc())) 
                    ++docsRetrieved;
            }
        }
        boolean allHitsRetrieved = hitsRetrieved == hitsCounted;
        return Pair.of(new ResultsStatsStatic(hitsRetrieved, hitsCounted, maxHitsStatsOfSource), new ResultsStatsStatic(docsRetrieved, allHitsRetrieved ? docsRetrieved : -1, maxDocsStatsOfSource));        
    }
}

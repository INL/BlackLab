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

import org.apache.lucene.search.Query;

import nl.inl.blacklab.forwardindex.FiidLookup;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * Groups results on the basis of a list of criteria.
 *
 * This class allows random access to the groups, and each group provides random
 * access to the hits. Note that this means that all hits found must be
 * retrieved, which may be infeasible for large results sets.
 */
public class HitGroups extends Results<HitGroup> implements ResultGroups<Hit> {

    /**
     * Construct HitGroups from a list of HitGroup instances.
     *
     * @param queryInfo query info
     * @param results list of groups
     * @param groupCriteria what hits would be grouped by
     * @param sampleParameters how groups were sampled, or null if not applicable
     * @param windowStats what groups window this is, or null if not applicable
     * @return grouped hits
     */
    public static HitGroups fromList(QueryInfo queryInfo, List<HitGroup> results, HitProperty groupCriteria, SampleParameters sampleParameters, WindowStats windowStats) {
        return new HitGroups(queryInfo, results, groupCriteria, sampleParameters, windowStats);
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

    public static HitGroups tokenFrequencies(QueryInfo queryInfo, Query filterQuery, HitProperty property, int maxHits) {
        return HitGroupsTokenFrequencies.get(queryInfo, filterQuery, property, maxHits);
    }

    private HitProperty criteria;

    /**
     * The groups.
     */
    // private Map<PropertyValue, HitGroup> groups = new HashMap<>();
    private Map<PropertyValue, HitGroup> groups = new HashMap<>();

    /**
     * Total number of hits.
     */
    private int totalHits = 0;

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
        Map<PropertyValue, List<Hit>> groupLists = new HashMap<>();
        Map<PropertyValue, Integer> groupSizes = new HashMap<>();
        resultObjects = 0;
        for (Hit hit: hits) {
            PropertyValue identity = criteria.get(hit);
            List<Hit> group = groupLists.get(identity);
            if (group == null) {
                group = new ArrayList<>();
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
            totalHits++;
        }
        resultObjects += groupLists.size();
        for (Map.Entry<PropertyValue, List<Hit>> e : groupLists.entrySet()) {
            PropertyValue groupId = e.getKey();
            List<Hit> hitList = e.getValue();
            Integer groupSize = groupSizes.get(groupId);
            HitGroup group = HitGroup.fromList(queryInfo(), groupId, hitList, hits.capturedGroups(), groupSize);
            groups.put(groupId, group);
            results.add(group);
        }
    }

    protected HitGroups(QueryInfo queryInfo, List<HitGroup> groups, HitProperty groupCriteria, SampleParameters sampleParameters, WindowStats windowStats) {
        super(queryInfo);
        this.criteria = groupCriteria;
        this.windowStats = windowStats;
        this.sampleParameters = sampleParameters;
        resultObjects = 0;
        for (HitGroup group: groups) {
            if (group.size() > largestGroupSize)
                largestGroupSize = group.size();
            totalHits += group.size();
            results.add(group);
            this.groups.put(group.identity(), group);
            resultObjects += group.numberOfStoredResults() + 1;
        }
    }


    @Override
    public HitProperty groupCriteria() {
        return criteria;
    }

    @Override
    public <P extends ResultProperty<HitGroup>> HitGroups sort(P sortProp) {
        List<HitGroup> sorted = Results.doSort(this, sortProp);
        return HitGroups.fromList(queryInfo(), sorted, criteria, (SampleParameters)null, (WindowStats)null);
    }

    @Override
    protected void ensureResultsRead(int number) {
        // NOP
    }

    /**
     * Take a sample of hits by wrapping an existing Hits object.
     *
     * @param sampleParameters sample parameters
     * @return the sample
     */
    @Override
    public HitGroups sample(SampleParameters sampleParameters) {
        return HitGroups.fromList(queryInfo(), Results.doSample(this, sampleParameters), groupCriteria(), sampleParameters, (WindowStats)null);
    }

    /**
     * Get the total number of hits
     *
     * @return the number of hits
     */
    @Override
    public int sumOfGroupSizes() {
        return totalHits;
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
        return HitGroups.fromList(queryInfo(), resultsWindow, criteria, (SampleParameters)null, windowStats);
    }

    @Override
    public HitGroups filter(ResultProperty<HitGroup> property, PropertyValue value) {
        List<HitGroup> list = Results.doFilter(this, property, value);
        return HitGroups.fromList(queryInfo(), list, groupCriteria(), (SampleParameters)null, (WindowStats)null);
    }

    @Override
    public ResultGroups<HitGroup> group(ResultProperty<HitGroup> criteria, int maxResultsToStorePerGroup) {
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
        return HitGroups.fromList(queryInfo(), truncatedGroups, criteria, (SampleParameters)null, windowStats);
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

    
    public ResultsStats hitsStats() {
        return new ResultsStats() {
            
            @Override
            public int processedTotal() {
                return HitGroups.this.totalHits;
            }
            
            @Override
            public int processedSoFar() {
                return processedTotal();
            }
            
            @Override
            public boolean processedAtLeast(int lowerBound) {
                return processedTotal() >= lowerBound;
            }
            
            @Override
            public MaxStats maxStats() {
                return new MaxStats(false, false); // TODO 
            }
            
            @Override
            public boolean done() {
                return true;
            }
            
            @Override
            public int countedTotal() {
                return processedTotal();
            }
            
            @Override
            public int countedSoFar() {
                return processedTotal();
            }
        };
    }
}

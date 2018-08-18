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

import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.ResultProperty;

/**
 * Applies grouping to the results in a DocResults object.
 */
public class DocGroups extends Results<DocGroup> implements ResultGroups<DocResult> {
    
    /**
     * Construct a DocGroups from a list of groups.
     * 
     * @param queryInfo query info
     * @param groups list of groups to wrap
     * @param groupBy what the documents were grouped by
     * @param sampleParameters sample parameters if this is a sample, null otherwise
     * @param windowStats window stats if this is a window, null otherwise
     * @return document groups
     */
    public static DocGroups fromList(QueryInfo queryInfo, List<DocGroup> groups, ResultProperty<DocResult> groupBy, SampleParameters sampleParameters, WindowStats windowStats) {
        return new DocGroups(queryInfo, groups, groupBy, sampleParameters, windowStats);
    }
    
    private Map<PropertyValue, DocGroup> groups = new HashMap<>();

    private int largestGroupSize = 0;

    private int totalResults = 0;

    private ResultProperty<DocResult> groupBy;
    
    private WindowStats windowStats;
    
    private SampleParameters sampleParameters;
    
    protected DocGroups(QueryInfo queryInfo, List<DocGroup> groups, ResultProperty<DocResult> groupBy, SampleParameters sampleParameters, WindowStats windowStats) {
        super(queryInfo);
        this.groupBy = groupBy;
        this.windowStats = windowStats;
        this.sampleParameters = sampleParameters;
        for (DocGroup group: groups) {
            if (group.size() > largestGroupSize)
                largestGroupSize = group.size();
            totalResults += group.size();
            results.add(group);
            this.groups.put(group.identity(), group);
        }
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
    public DocGroup get(PropertyValue groupId) {
        return groups.get(groupId);
    }

    @Override
    public <P extends ResultProperty<DocGroup>> DocGroups sort(P sortProp) {
        List<DocGroup> sorted = Results.doSort(this, sortProp);
        return DocGroups.fromList(queryInfo(), sorted, groupBy, (SampleParameters)null, (WindowStats)null);
    }

    @Override
    public int largestGroupSize() {
        return largestGroupSize;
    }

    @Override
    public int sumOfGroupSizes() {
        return totalResults;
    }

    @Override
    public ResultProperty<DocResult> groupCriteria() {
        return groupBy;
    }
    
    @Override
    public DocGroups window(int first, int number) {
        List<DocGroup> resultsWindow = Results.doWindow(this, first, number);
        boolean hasNext = resultsProcessedAtLeast(first + resultsWindow.size() + 1);
        WindowStats windowStats = new WindowStats(hasNext, first, number, resultsWindow.size());
        return DocGroups.fromList(queryInfo(), resultsWindow, groupBy, (SampleParameters)null, windowStats);
    }

    @Override
    protected void ensureResultsRead(int number) {
        // NOP
    }

    @Override
    public DocGroups filter(ResultProperty<DocGroup> property, PropertyValue value) {
        List<DocGroup> list = Results.doFilter(this, property, value);
        return new DocGroups(queryInfo(), list, groupCriteria(), (SampleParameters)null, (WindowStats)null);
    }

    @Override
    public ResultGroups<DocGroup> group(ResultProperty<DocGroup> criteria, int maxResultsToStorePerGroup) {
        throw new UnsupportedOperationException("Cannot group DocGroups");
    }

    @Override
    public DocGroups withFewerStoredResults(int maximumNumberOfResultsPerGroup) {
        if (maximumNumberOfResultsPerGroup < 0)
            maximumNumberOfResultsPerGroup = Integer.MAX_VALUE;
        List<DocGroup> truncatedGroups = new ArrayList<DocGroup>();
        for (DocGroup group: results) {
            List<DocResult> truncatedList = group.storedResults().window(0, maximumNumberOfResultsPerGroup).resultsList();
            DocGroup newGroup = DocGroup.fromList(queryInfo(), group.identity(), truncatedList, group.size());
            truncatedGroups.add(newGroup);
        }
        return DocGroups.fromList(queryInfo(), truncatedGroups, groupBy, (SampleParameters)null, windowStats);
    }

    /**
     * Take a sample of hits by wrapping an existing Hits object.
     *
     * @param sampleParameters sample parameters 
     * @return the sample
     */
    @Override
    public DocGroups sample(SampleParameters sampleParameters) {
        return DocGroups.fromList(queryInfo(), Results.doSample(this, sampleParameters), groupCriteria(), sampleParameters, (WindowStats)null);
    }
}

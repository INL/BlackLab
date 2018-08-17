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

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
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
     * @param windowStats window stats if this is a window, null otherwise
     * @return document groups
     */
    public static DocGroups fromList(QueryInfo queryInfo, List<DocGroup> groups, ResultProperty<DocResult> groupBy, WindowStats windowStats) {
        return new DocGroups(queryInfo, groups, groupBy, windowStats);
    }
    
    private Map<PropertyValue, DocGroup> groups = new HashMap<>();

    private int largestGroupSize = 0;

    private int totalResults = 0;

    private ResultProperty<DocResult> groupBy;
    
    private WindowStats windowStats;
    
    @Override
    public WindowStats windowStats() {
        return windowStats;
    }

    protected DocGroups(QueryInfo queryInfo, List<DocGroup> groups, ResultProperty<DocResult> groupBy, WindowStats windowStats) {
        super(queryInfo);
        this.groupBy = groupBy;
        this.windowStats = windowStats;
        for (DocGroup group: groups) {
            if (group.size() > largestGroupSize)
                largestGroupSize = group.size();
            totalResults += group.size();
            results.add(group);
            this.groups.put(group.getIdentity(), group);
        }
    }

    @Override
    public DocGroup get(PropertyValue groupId) {
        return groups.get(groupId);
    }

    @Override
    public <P extends ResultProperty<DocGroup>> DocGroups sortedBy(P sortProp) {
        ensureAllHitsRead();
        List<DocGroup> sorted = new ArrayList<>(results);
        sorted.sort(sortProp);
        return new DocGroups(queryInfo(), sorted, groupBy, null);
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
    public ResultProperty<DocResult> getGroupCriteria() {
        return groupBy;
    }

    @Override
    public Results<DocGroup> window(int first, int windowSize) {
        int to = first + windowSize;
        if (to >= 0)
            ensureResultsRead(to + 1);
        if (first < 0 || first >= results.size())
            throw new BlackLabRuntimeException("First hit out of range");
        if (results.size() < to)
            to = results.size();
        List<DocGroup> list = new ArrayList<>(results.subList(first, to)); // copy to avoid 'memleaks' from .subList()
        boolean hasNext = results.size() > to;
        return new DocGroups(queryInfo(), list, groupBy, new WindowStats(hasNext, first, windowSize, list.size()));
    }

    @Override
    protected void ensureResultsRead(int number) {
        // NOP
    }

    @Override
    public DocGroups filteredBy(ResultProperty<DocGroup> property, PropertyValue value) {
        List<DocGroup> list = stream().filter(g -> property.get(g).equals(value)).collect(Collectors.toList());
        return new DocGroups(queryInfo(), list, getGroupCriteria(), null);
    }

    @Override
    public ResultGroups<DocGroup> groupedBy(ResultProperty<DocGroup> criteria, int maxResultsToStorePerGroup) {
        throw new UnsupportedOperationException("Cannot group DocGroups");
    }

    @Override
    public DocGroups withFewerStoredResults(int maximumNumberOfResultsPerGroup) {
        if (maximumNumberOfResultsPerGroup < 0)
            maximumNumberOfResultsPerGroup = Integer.MAX_VALUE;
        List<DocGroup> truncatedGroups = new ArrayList<DocGroup>();
        for (DocGroup group: results) {
            List<DocResult> truncatedList = group.getStoredResults().window(0, maximumNumberOfResultsPerGroup).resultsList();
            DocGroup newGroup = DocGroup.fromList(queryInfo(), group.getIdentity(), truncatedList, group.size());
            truncatedGroups.add(newGroup);
        }
        return new DocGroups(queryInfo(), truncatedGroups, groupBy, windowStats);
    }
}

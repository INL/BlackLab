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
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * Groups results on the basis of a list of criteria, and provide random access
 * to the resulting groups.
 *
 * This implementation doesn't care in what order the spans appear, it will just
 * retrieve all of them and put each of them in a group. This takes more memory
 * and time than if the spans to be grouped are sequential (in which case you
 * should use ResultsGrouperSequential).
 */
public class HitGroupsImpl extends HitGroups {
    /**
     * Don't use this; use Hits.groupedBy().
     * 
     * @param hits hits to group
     * @param criteria criteria to group by
     * @return grouped hits
     */
    static HitGroups fromHits(Hits hits, HitProperty criteria) {
        return new HitGroupsImpl(hits, criteria);
    }

    /**
     * The groups.
     */
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
    
    /**
     * Construct a ResultsGrouper object, by grouping the supplied hits.
     *
     * NOTE: this will be made package-private in a future release. Use
     * Hits.groupedBy(criteria) instead.
     *
     * @param hits the hits to group
     * @param criteria the criteria to group on
     */
    HitGroupsImpl(Hits hits, HitProperty criteria) {
        super(hits.queryInfo(), criteria);
        
        List<Annotation> requiredContext = criteria.needsContext();
        criteria = criteria.copyWith(hits, requiredContext == null ? null : new Contexts(hits, requiredContext, criteria.needsContextSize(hits.index())));
        
        //Thread currentThread = Thread.currentThread();
        Map<PropertyValue, List<Hit>> groupLists = new HashMap<>();
        for (Hit hit: hits) {
            PropertyValue identity = criteria.get(hit);
            List<Hit> group = groupLists.get(identity);
            if (group == null) {
                group = new ArrayList<>();
                groupLists.put(identity, group);
            }
            group.add(hit);
            if (group.size() > largestGroupSize)
                largestGroupSize = group.size();
            totalHits++;
        }
        for (Map.Entry<PropertyValue, List<Hit>> e : groupLists.entrySet()) {
            PropertyValue groupId = e.getKey();
            List<Hit> hitList = e.getValue();
            HitGroup group = new HitGroup(queryInfo(), groupId, hitList);
            groups.put(groupId, group);
            results.add(group);
        }
    }

    public HitGroupsImpl(QueryInfo queryInfo, List<HitGroup> sorted, HitProperty groupCriteria, WindowStats windowStats) {
        super(queryInfo, groupCriteria);
        this.windowStats = windowStats;
        for (HitGroup group: sorted) {
            if (group.size() > largestGroupSize)
                largestGroupSize = group.size();
            totalHits += group.size();
            results.add(group);
            groups.put(group.getIdentity(), group);
        }
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
    public Results<HitGroup> window(int first, int windowSize) {
        int to = first + windowSize;
        if (to >= 0)
            ensureResultsRead(to + 1);
        if (first < 0 || first >= results.size())
            throw new BlackLabRuntimeException("First hit out of range");
        if (results.size() < to)
            to = results.size();
        List<HitGroup> list = new ArrayList<>(results.subList(first, to)); // copy to avoid 'memleaks' from .subList()
        boolean hasNext = results.size() > to;
        return new HitGroupsImpl(queryInfo(), list, criteria, new WindowStats(hasNext, first, windowSize, list.size()));
    }

    @Override
    public HitGroups filteredBy(ResultProperty<HitGroup> property, PropertyValue value) {
        List<HitGroup> list = results.stream().filter(g -> property.get(g).equals(value)).collect(Collectors.toList());
        return new HitGroupsImpl(queryInfo(), list, getGroupCriteria(), null);
    }

    @Override
    public ResultGroups<HitGroup> groupedBy(ResultProperty<HitGroup> criteria) {
        throw new UnsupportedOperationException("Cannot group HitGroups");
    }

}

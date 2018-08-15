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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.resultproperty.ComparatorDocGroupProperty;
import nl.inl.blacklab.resultproperty.DocGroupProperty;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.ResultProperty;

/**
 * Applies grouping to the results in a DocResults object.
 */
public class DocGroups extends Results<DocGroup> implements ResultGroups<DocResult> {
    Map<PropertyValue, DocGroup> groups = new HashMap<>();

    private int largestGroupSize = 0;

    private int totalResults = 0;

    private DocProperty groupBy;

    private QueryInfo queryInfo;

    /**
     * Constructor. Fills the groups from the given document results.
     *
     * @param docResults the results to group.
     * @param groupBy the criterium to group on.
     */
    public DocGroups(DocResults docResults, DocProperty groupBy) {
        super(docResults.queryInfo());
        this.groupBy = groupBy;
        //Thread currentThread = Thread.currentThread();
        Map<PropertyValue, List<DocResult>> groupLists = new HashMap<>();
        for (DocResult r : docResults) { // TODO inconsistency compared to hits within groups, hitgroups ignore sorting of the source data, docgroups don't
            PropertyValue groupId = groupBy.get(r);
            List<DocResult> group = groupLists.get(groupId);
            if (group == null) {
                group = new ArrayList<>();
                groupLists.put(groupId, group);
            }
            group.add(r);
            if (group.size() > largestGroupSize)
                largestGroupSize = group.size();
            totalResults++;
        }
        for (Map.Entry<PropertyValue, List<DocResult>> e : groupLists.entrySet()) {
            DocGroup docGroup = new DocGroup(docResults.queryInfo(), e.getKey(), e.getValue());
            groups.put(e.getKey(), docGroup);
            results.add(docGroup);
        }
    }

    private DocGroups(QueryInfo queryInfo, List<DocGroup> sorted, DocProperty groupBy) {
        super(queryInfo);
        this.groupBy = groupBy;
        for (DocGroup group: sorted) {
            if (group.size() > largestGroupSize)
                largestGroupSize = group.size();
            totalResults += group.size();
            results.add(group);
            groups.put(group.getIdentity(), group);
        }
    }

    @Override
    public QueryInfo queryInfo() {
        return queryInfo;
    }

    public Collection<DocGroup> getGroups() {
        return Collections.unmodifiableCollection(results);
    }

    @Override
    public DocGroup get(PropertyValue groupId) {
        return groups.get(groupId);
    }

    /**
     * Order the groups based on the specified group property.
     *
     * @param prop the property to sort on
     * @param sortReverse if true, perform reverse sort
     */
    public void sort(DocGroupProperty prop, boolean sortReverse) {
        Comparator<DocGroup> comparator = new ComparatorDocGroupProperty(prop, sortReverse);
        results.sort(comparator);
    }

    /**
     * Return a new Hits object with these hits sorted by the given property.
     *
     * This keeps the existing sort (or lack of one) intact and allows you to cache
     * different sorts of the same resultset. The hits themselves are reused between
     * the two Hits instances, so not too much additional memory is used.
     *
     * @param sortProp the hit property to sort on
     * @return a new Hits object with the same hits, sorted in the specified way
     */
    @Override
    public <P extends ResultProperty<DocGroup>> Results<DocGroup> sortedBy(P sortProp) {
        try {
            ensureAllHitsRead();
        } catch (InterruptedException e) {
            // Thread was interrupted; abort operation
            // and let client decide what to do
            Thread.currentThread().interrupt();
        }
        List<DocGroup> sorted = new ArrayList<>(results);
        sorted.sort(sortProp);
        return new DocGroups(queryInfo, sorted, groupBy);
    }

    public void sort(DocGroupProperty prop) {
        sort(prop, false);
    }

    @Override
    public Iterator<DocGroup> iterator() {
        return getGroups().iterator();
    }

    @Override
    public int getLargestGroupSize() {
        return largestGroupSize;
    }

    @Override
    public int getTotalResults() {
        return totalResults;
    }

    public DocProperty getGroupCriteria() {
        return groupBy;
    }

    @Override
    public DocGroup get(int i) {
        return results.get(i);
    }

//    @Override
//    public void add(DocGroup obj) {
//        groups.put(obj.getIdentity(), obj);
//        orderedGroups.add(obj);
//    }

    @Override
    public int size() {
        return groups.size();
    }

    @Override
    public Results<DocGroup> window(int first, int windowSize) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void ensureResultsRead(int number) throws InterruptedException {
        // NOP
    }

}

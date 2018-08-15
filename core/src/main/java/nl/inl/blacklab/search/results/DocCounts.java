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
 * Counts the number of documents that have a certain property.
 *
 * Similar to grouping documents, but doesn't store the documents and hits.
 *
 * Useful for faceted search.
 */
public class DocCounts extends Results<DocCount> {
    Map<PropertyValue, DocCount> counts = new HashMap<>();

    List<DocCount> orderedGroups = new ArrayList<>();

    private int largestGroupSize = 0;

    private int totalResults = 0;

    private DocProperty countBy;

    /**
     * Constructor. Fills the groups from the given document results.
     *
     * @param docResults the results to group.
     * @param countBy the criterium to group on.
     */
    DocCounts(DocResults docResults, DocProperty countBy) {
        super(docResults.queryInfo());
        this.countBy = countBy;
        //Thread currentThread = Thread.currentThread();
        for (DocResult r : docResults) {
            PropertyValue groupId = countBy.get(r);
            DocCount count = counts.get(groupId);
            if (count == null) {
                count = new DocCount(docResults.queryInfo(), groupId);
                counts.put(groupId, count);
            }
            count.increment();
            if (count.size() > largestGroupSize)
                largestGroupSize = count.size();
            totalResults++;
        }
        for (DocCount c : counts.values()) {
            orderedGroups.add(c);
        }
    }
    
    public Collection<DocCount> getCounts() {
        return Collections.unmodifiableCollection(orderedGroups);
    }

    public Integer getCount(PropertyValue groupId) {
        return counts.get(groupId).size();
    }

    public void sort(DocGroupProperty prop, boolean sortReverse) {
        Comparator<DocGroup> comparator = new ComparatorDocGroupProperty(prop, sortReverse);
        orderedGroups.sort(comparator);
    }

    public void sort(DocGroupProperty prop) {
        sort(prop, false);
    }

    @Override
    public Iterator<DocCount> iterator() {
        return getCounts().iterator();
    }

    public int numberOfGroups() {
        return counts.size();
    }

    public int getLargestGroupSize() {
        return largestGroupSize;
    }

    public int getTotalResults() {
        return totalResults;
    }

    public ResultProperty<Group<Hit>> getGroupCriteria() {
        return countBy;
    }

    @Override
    public int size() {
        return numberOfGroups();
    }

    @Override
    public DocCount get(int i) {
        return orderedGroups.get(i);
    }

    @Override
    public Results<DocCount> window(int first, int windowSize) {
        throw new UnsupportedOperationException();
    }

}

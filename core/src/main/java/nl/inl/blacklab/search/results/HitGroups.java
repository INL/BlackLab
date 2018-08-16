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
import java.util.List;

import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.ResultProperty;

/**
 * Groups results on the basis of a list of criteria.
 *
 * This class allows random access to the groups, and each group provides random
 * access to the hits. Note that this means that all hits found must be
 * retrieved, which may be unfeasible for large results sets.
 */
public abstract class HitGroups extends Results<HitGroup> implements ResultGroups<Hit> {

    protected HitProperty criteria;

    public HitGroups(QueryInfo queryInfo, HitProperty groupCriteria) {
        super(queryInfo);
        this.criteria = groupCriteria;
    }
    
    @Override
    public abstract HitGroup get(PropertyValue identity);

    @Override
    public HitProperty getGroupCriteria() {
        return criteria;
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
    public <P extends ResultProperty<HitGroup>> HitGroups sortedBy(P sortProp) {
        ensureAllHitsRead();
        List<HitGroup> sorted = new ArrayList<>(results);
        sorted.sort(sortProp);
        return new HitGroupsImpl(queryInfo(), sorted, getGroupCriteria(), null);
    }

    /**
     * Get the total number of hits
     *
     * @return the number of hits
     */
    @Override
    public abstract int sumOfGroupSizes();

    /**
     * Return the size of the largest group
     *
     * @return size of the largest group
     */
    @Override
    public abstract int largestGroupSize();
    
    @Override
    protected void ensureResultsRead(int number) {
        // NOP
    }
}

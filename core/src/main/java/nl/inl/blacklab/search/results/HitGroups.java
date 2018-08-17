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
        return new HitGroupsImpl(queryInfo, results, groupCriteria, sampleParameters, windowStats);
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
        return new HitGroupsImpl(hits, criteria, maxResultsToStorePerGroup);
    }

    protected HitProperty criteria;

    protected HitGroups(QueryInfo queryInfo, HitProperty groupCriteria) {
        super(queryInfo);
        this.criteria = groupCriteria;
    }
    
    @Override
    public abstract HitGroup get(PropertyValue identity);

    @Override
    public HitProperty getGroupCriteria() {
        return criteria;
    }

    @Override
    public <P extends ResultProperty<HitGroup>> HitGroups sortedBy(P sortProp) {
        List<HitGroup> sorted = Results.doSort(this, sortProp);
        return HitGroups.fromList(queryInfo(), sorted, criteria, (SampleParameters)null, (WindowStats)null);
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

    /**
     * Take a sample of hits by wrapping an existing Hits object.
     *
     * @param sampleParameters sample parameters 
     * @return the sample
     */
    @Override
    public HitGroups sample(SampleParameters sampleParameters) {
        return HitGroups.fromList(queryInfo(), Results.doSample(this, sampleParameters), getGroupCriteria(), sampleParameters, (WindowStats)null);
    }
}

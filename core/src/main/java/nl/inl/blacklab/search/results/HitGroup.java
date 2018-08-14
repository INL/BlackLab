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

import nl.inl.blacklab.resultproperty.PropertyValue;

/**
 * A group of results, with its group identity and the results themselves, that
 * you can access randomly (i.e. you can obtain a list of Hit objects)
 */
public class HitGroup extends Group {
    Hits results;

    HitGroup(QueryInfo queryInfo, PropertyValue groupIdentity) {
        super(groupIdentity);
        results = Hits.emptyList(queryInfo);
    }

    /**
     * Wraps a list of Hit objects with the HitGroup interface.
     *
     * NOTE: the list is not copied!
     *
     * @param queryInfo query info
     * @param hits the hits
     */
    HitGroup(QueryInfo queryInfo, PropertyValue groupIdentity, List<Hit> hits) {
        super(groupIdentity);
        results = Hits.fromList(queryInfo, hits);
    }

    /**
     * Wraps a list of Hit objects with the HitGroup interface.
     *
     * NOTE: the list is not copied!
     *
     * @param queryInfo query info
     * @param hits the hits
     */
    HitGroup(PropertyValue groupIdentity, Hits hits) {
        super(groupIdentity);
        results = hits;
    }

    public Hits getHits() {
        return results;
    }

    public int size() {
        return results.size();
    }

    @Override
    public String toString() {
        return "GroupOfHits, identity = " + groupIdentity + ", size = " + results.size();
    }
}

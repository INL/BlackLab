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

import nl.inl.blacklab.resultproperty.PropertyValue;

/**
 * A group of results, with its group identity and the results themselves.
 * 
 * @param <T> result type, e.g. Hit 
 */
public abstract class Group<T> implements Result<Group<T>> {
    protected PropertyValue groupIdentity;

    public Group(PropertyValue groupIdentity) {
        this.groupIdentity = groupIdentity;
    }

    public PropertyValue getIdentity() {
        return groupIdentity;
    }

    public abstract void add(T obj);

    public abstract int size();

    public abstract <R extends Results<T>> R getResults();
    
    @Override
    public int compareTo(Group<T> o) {
        return getIdentity().compareTo(o.getIdentity());
    }

}

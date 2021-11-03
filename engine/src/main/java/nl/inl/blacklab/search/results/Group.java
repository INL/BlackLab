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
import nl.inl.blacklab.resultproperty.ResultProperty;

/**
 * A group of results, with its group identity and the results themselves.
 * 
 * @param <T> result type, e.g. Hit 
 */
public abstract class Group<T> implements Result<Group<T>> {
    
    protected PropertyValue groupIdentity;

    private Results<T, ? extends ResultProperty<T>> storedResults;
    
    private int totalSize;

    protected Group(PropertyValue groupIdentity, Results<T, ? extends ResultProperty<T>> storedResults, int totalSize) {
        this.groupIdentity = groupIdentity;
        this.storedResults = storedResults;
        this.totalSize = totalSize;
    }

    public PropertyValue identity() {
        return groupIdentity;
    }
    
    public Results<T, ? extends ResultProperty<T>> storedResults() {
        return storedResults;
    }
    
    public int numberOfStoredResults() {
        return storedResults != null ? storedResults.size() : 0;
    }

    public int size() {
        return totalSize;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(id=" + identity() + ", size=" + size() + ")";
    }

    @Override
    public int compareTo(Group<T> o) {
        return identity().compareTo(o.identity());
    }

}

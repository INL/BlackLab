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
package nl.inl.blacklab.resultproperty;

import java.util.Arrays;
import java.util.List;

import nl.inl.blacklab.search.results.Group;

/**
 * Abstract base class for a property of a group op results.
 * 
 * @param <T> type of result, e.g. Hit
 * @param <G> group type, e.g. HitGroup
 */
public abstract class GroupProperty<T, G extends Group<T>> implements ResultProperty<G> {

    /** Reverse comparison result or not? */
    protected boolean reverse;
    
    GroupProperty(GroupProperty<T, G> prop, boolean invert) {
        this.reverse = invert ? !prop.reverse : prop.reverse;
    }
    
    public GroupProperty() {
        this.reverse = false;
    }

    @Override
    public abstract PropertyValue get(G result);

    @Override
    public abstract int compare(G a, G b);

    @Override
    public abstract String serialize();

    /**
     * Used by subclasses to add a dash for reverse when serializing
     * 
     * @return either a dash or the empty string
     */
    protected String serializeReverse() {
        return reverse ? "-" : "";
    }

    /**
     * Is the comparison reversed?
     * 
     * @return true if it is, false if not
     */
    @Override
    public boolean isReverse() {
        return reverse;
    }

    /**
     * Reverse the comparison.
     * @return reversed group property 
     */
    @Override
    public abstract GroupProperty<T, G> reverse();

    @Override
    public String toString() {
        return serialize();
    }
    
    @Override
    public List<String> propNames() {
        return Arrays.asList(name());
    }

}

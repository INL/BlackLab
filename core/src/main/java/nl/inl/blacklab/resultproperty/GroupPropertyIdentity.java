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

import nl.inl.blacklab.search.results.Group;

/**
 * Abstract base class for a property of a hit, like document title, hit text,
 * right context, etc.
 * 
 * @param <T> result type, e.g. Hit
 * @param <G> group type, e.g. HitGroup
 */
public class GroupPropertyIdentity<T, G extends Group<T>> extends GroupProperty<T, G> {
    
    GroupPropertyIdentity(GroupPropertyIdentity<T, G> prop, boolean invert) {
        super(prop, invert);
    }
    
    public GroupPropertyIdentity() {
        // NOP
    }
    
    @Override
    public PropertyValue get(G result) {
        return result.getIdentity();
    }

    @Override
    public int compare(G a, G b) {
        if (reverse)
            return b.getIdentity().compareTo(a.getIdentity());
        return a.getIdentity().compareTo(b.getIdentity());
    }

    @Override
    public String serialize() {
        return serializeReverse() + "identity";
    }

    @Override
    public GroupPropertyIdentity<T, G> reverse() {
        return new GroupPropertyIdentity<>(this, true);
    }

    @Override
    public String getName() {
        return "group: identity";
    }

}

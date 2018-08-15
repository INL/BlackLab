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
import nl.inl.blacklab.search.results.HitGroup;

/**
 * Abstract base class for a property of a hit, like document title, hit text,
 * right context, etc.
 * 
 * @param <T> result type, e.g. Hit 
 */
public class GroupPropertySize<T> extends GroupProperty<T> {
    
    GroupPropertySize(GroupPropertySize<T> prop, boolean invert) {
        super(prop, invert);
    }
    
    public GroupPropertySize() {
        // NOP
    }
    
    @Override
    public PropertyValueInt get(Group<T> result) {
        return new PropertyValueInt(((HitGroup) result).size());
    }

    @Override
    public int compare(Group<T> a, Group<T> b) {
        if (reverse)
            return ((HitGroup) b).size() - ((HitGroup) a).size();
        return ((HitGroup) a).size() - ((HitGroup) b).size();
    }

    @Override
    public boolean defaultSortDescending() {
        return !reverse;
    }

    @Override
    public String serialize() {
        return serializeReverse() + "size";
    }

    @Override
    public GroupProperty<T> reverse() {
        return new GroupPropertySize<T>(this, true);
    }

    @Override
    public String getName() {
        return "group: size";
    }

}

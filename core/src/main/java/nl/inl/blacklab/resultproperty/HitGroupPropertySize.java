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

import nl.inl.blacklab.search.results.HitGroup;

public class HitGroupPropertySize extends HitGroupProperty {
    
    HitGroupPropertySize(HitGroupPropertySize prop, boolean invert) {
        super(prop, invert);
    }
    
    public HitGroupPropertySize() {
        super();
    }

    @Override
    protected boolean sortDescendingByDefault() {
        return true;
    }
    
    @Override
    public PropertyValueInt get(HitGroup result) {
        return new PropertyValueInt(result.size());
    }

    @Override
    public int compare(HitGroup a, HitGroup b) {
        return reverse ? b.size() - a.size() : a.size() - b.size();
    }

    @Override
    public String serialize() {
        return serializeReverse() + "size";
    }

    @Override
    public HitGroupPropertySize reverse() {
        return new HitGroupPropertySize(this, true);
    }

    @Override
    public String name() {
        return "group: size";
    }
}

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

public class HitGroupPropertyIdentity extends HitGroupProperty {
    
    HitGroupPropertyIdentity(HitGroupPropertyIdentity prop, boolean invert) {
        super(prop, invert);
    }
    
    public HitGroupPropertyIdentity() {
        super();
    }
    
    @Override
    public PropertyValue get(HitGroup result) {
        return result.identity();
    }

    @Override
    public int compare(HitGroup a, HitGroup b) {
        if (reverse)
            return b.identity().compareTo(a.identity());
        return a.identity().compareTo(b.identity());
    }

    @Override
    public String serialize() {
        return serializeReverse() + "identity";
    }

    @Override
    public HitGroupPropertyIdentity reverse() {
        return new HitGroupPropertyIdentity(this, true);
    }

    @Override
    public String name() {
        return "group: identity";
    }
}

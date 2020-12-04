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

import nl.inl.blacklab.search.results.Contexts;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Results;

/**
 * A hit property for sorting on hit token position. Usually to be combined with
 * sorting on document id, for a fast and reproducible sort.
 */
public class HitPropertyHitPosition extends HitProperty {

    static HitPropertyHitPosition deserializeProp() {
        return new HitPropertyHitPosition();
    }

    HitPropertyHitPosition(HitPropertyHitPosition prop, Results<Hit> hits, boolean invert) {
        super(prop, hits, null, invert);
    }
    
    public HitPropertyHitPosition() {
        super();
    }

    @Override
    public HitProperty copyWith(Results<Hit> newHits, Contexts contexts, boolean invert) {
        return new HitPropertyHitPosition(this, newHits, invert);
    }

    @Override
    public PropertyValueInt get(Hit result) {
        return new PropertyValueInt(result.start());
    }

    @Override
    public String name() {
        return "hit: position";
    }

    @Override
    public int compare(Hit a, Hit b) {
        if (a.start() == b.start())
            return reverse ? b.end() - a.end() : a.end() - b.end();
        return reverse ? b.start() - a.start() : a.start() - b.start();
    }

    @Override
    public String serialize() {
        return serializeReverse() + "hitposition";
    }
    
    @Override
    public boolean isDocPropOrHitText() {
        return false;
    }
}

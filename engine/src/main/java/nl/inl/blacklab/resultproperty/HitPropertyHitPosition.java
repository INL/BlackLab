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

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Contexts;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.Hits.HitsArrays;

/**
 * A hit property for sorting on hit token position. Usually to be combined with
 * sorting on document id, for a fast and reproducible sort.
 */
public class HitPropertyHitPosition extends HitProperty {

    static HitPropertyHitPosition deserializeProp() {
        return new HitPropertyHitPosition();
    }

    HitPropertyHitPosition(HitPropertyHitPosition prop, Hits hits, boolean invert) {
        super(prop, hits, null, invert);
    }
    
    public HitPropertyHitPosition() {
        super();
    }

    @Override
    public HitProperty copyWith(Hits newHits, Contexts contexts, boolean invert) {
        return new HitPropertyHitPosition(this, newHits, invert);
    }

    @Override
    public PropertyValueInt get(int hitIndex) {
        return new PropertyValueInt(hits.hitsArrays().start(hitIndex));
    }

    @Override
    public String name() {
        return "hit: position";
    }

    @Override
    public int compare(int indexA, int indexB) {
        HitsArrays ha = hits.hitsArrays();
        int startA = ha.start(indexA);
        int startB = ha.start(indexB);
        int endA = ha.end(indexA);
        int endB = ha.end(indexB);
        
        if (startA == startB)
            return reverse ? endB - endA : endA - endB;
        return reverse ? startB - startA : startA - startB;
    }

    @Override
    public String serialize() {
        return serializeReverse() + "hitposition";
    }
    
    @Override
    public boolean isDocPropOrHitText() {
        return false;
    }
    
    @Override
    public ContextSize needsContextSize(BlackLabIndex index) {
        return null;
    }
}

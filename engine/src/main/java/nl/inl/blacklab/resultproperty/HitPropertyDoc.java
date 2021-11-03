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
 * A hit property for grouping per document.
 */
public class HitPropertyDoc extends HitProperty {

    static HitPropertyDoc deserializeProp(BlackLabIndex index) {
        return new HitPropertyDoc(index);
    }

    private BlackLabIndex index;

    HitPropertyDoc(HitPropertyDoc prop, Hits hits, boolean invert) {
        super(prop, hits, null, invert);
        this.index = hits.index();
    }

    public HitPropertyDoc(BlackLabIndex index) {
        super();
        this.index = index;
    }

    @Override
    public HitProperty copyWith(Hits newHits, Contexts contexts, boolean invert) {
        return new HitPropertyDoc(this, newHits, invert);
    }

    @Override
    public PropertyValueDoc get(int hitIndex) {
        return new PropertyValueDoc(index.doc(hits.hitsArrays().doc(hitIndex)));
    }

    @Override
    public String name() {
        return "document";
    }

    @Override
    public int compare(int indexA, int indexB) {
        HitsArrays ha = hits.hitsArrays();
        int docA = ha.doc(indexA);
        int docB = ha.doc(indexB);
        return reverse ? docB - docA : docA - docB;
    }

    @Override
    public String serialize() {
        return serializeReverse() + "doc";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((index == null) ? 0 : index.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        HitPropertyDoc other = (HitPropertyDoc) obj;
        if (index == null) {
            if (other.index != null)
                return false;
        } else if (!index.equals(other.index))
            return false;
        return true;
    }

    @Override
    public DocProperty docPropsOnly() {
        DocPropertyId result = new DocPropertyId();
        return reverse ? result.reverse() : result;
    }

    @Override
    public PropertyValue docPropValues(PropertyValue value) {
        return value;
    }

    @Override
    public boolean isDocPropOrHitText() {
        return true;
    }
       
    @Override
    public ContextSize needsContextSize(BlackLabIndex index) {
        return null;
    }
}

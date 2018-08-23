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
import nl.inl.blacklab.search.results.Contexts;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Results;

/**
 * A hit property for grouping per document.
 */
public class HitPropertyDoc extends HitProperty {

    static HitPropertyDoc deserializeProp(BlackLabIndex index) {
        return new HitPropertyDoc(index);
    }

    private BlackLabIndex index;

    HitPropertyDoc(HitPropertyDoc prop, Results<Hit> hits, boolean invert) {
        super(prop, hits, null, invert);
        this.index = hits.index();
    }

    public HitPropertyDoc(BlackLabIndex index) {
        super();
        this.index = index;
    }

    @Override
    public HitProperty copyWith(Results<Hit> newHits, Contexts contexts, boolean invert) {
        return new HitPropertyDoc(this, newHits, invert);
    }

    @Override
    public PropertyValueDoc get(Hit result) {
        return new PropertyValueDoc(index.doc(result.doc()));
    }

    @Override
    public String name() {
        return "document";
    }

    @Override
    public int compare(Hit a, Hit b) {
        return reverse ? b.doc() - a.doc() : a.doc() - b.doc();
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
    
}

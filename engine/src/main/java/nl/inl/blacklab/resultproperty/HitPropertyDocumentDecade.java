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

import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Contexts;
import nl.inl.blacklab.search.results.Hits;

/**
 * A hit property for grouping on by decade based on a stored field in the
 * corresponding Lucene document containing a year.
 */
public class HitPropertyDocumentDecade extends HitProperty {

    /** The value we store when the decade is unknown */
    public static final int UNKNOWN_VALUE = 10_000_000;

    static HitPropertyDocumentDecade deserializeProp(BlackLabIndex index, String info) {
        return new HitPropertyDocumentDecade(index, index.metadataField(PropertySerializeUtil.unescapePart(info)));
    }

    private BlackLabIndex index;

    IndexReader reader;

    String fieldName;

    private DocPropertyDecade docPropertyDocumentDecade;

    HitPropertyDocumentDecade(HitPropertyDocumentDecade prop, Hits hits, boolean invert) {
        super(prop, hits, null, invert);
        this.index = prop.index;
        this.reader = index.reader();
        this.fieldName = prop.fieldName;
        this.docPropertyDocumentDecade = prop.docPropertyDocumentDecade;
    }

    public HitPropertyDocumentDecade(BlackLabIndex index, MetadataField field) {
        super();
        this.index = index;
        this.reader = index.reader();
        this.fieldName = field.name();
        this.docPropertyDocumentDecade = new DocPropertyDecade(index, fieldName);
    }

    @Override
    public HitProperty copyWith(Hits newHits, Contexts contexts, boolean invert) {
        return new HitPropertyDocumentDecade(this, newHits, invert);
    }

    @Override
    public PropertyValueDecade get(int hitIndex) {
        return docPropertyDocumentDecade.get(hits.hitsArrays().doc(hitIndex));
    }

    @Override
    public int compare(int indexA, int indexB) {
        return docPropertyDocumentDecade.compare(
            hits.hitsArrays().doc(indexA),
            hits.hitsArrays().doc(indexB)
        ) * (reverse ? -1 : 1);
    }

    @Override
    public String name() {
        return "document: " + docPropertyDocumentDecade.name();
    }

    @Override
    public String serialize() {
        return serializeReverse() + PropertySerializeUtil.combineParts("decade", fieldName);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((fieldName == null) ? 0 : fieldName.hashCode());
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
        HitPropertyDocumentDecade other = (HitPropertyDocumentDecade) obj;
        if (fieldName == null) {
            if (other.fieldName != null)
                return false;
        } else if (!fieldName.equals(other.fieldName))
            return false;
        return true;
    }

    @Override
    public DocProperty docPropsOnly() {
        return reverse ? docPropertyDocumentDecade.reverse() : docPropertyDocumentDecade;
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

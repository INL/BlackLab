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

/**
 * A hit property for grouping on a stored field in the corresponding Lucene
 * document.
 */
public class HitPropertyDocumentStoredField extends HitProperty {

    static HitPropertyDocumentStoredField deserializeProp(BlackLabIndex index, String info) {
        return new HitPropertyDocumentStoredField(index, PropertySerializeUtil.unescapePart(info));
    }

    final String fieldName;

    final private DocPropertyStoredField docPropStoredField;

    HitPropertyDocumentStoredField(HitPropertyDocumentStoredField prop, Hits hits, boolean invert) {
        super(prop, hits, null, invert);
        this.fieldName = prop.fieldName;
        this.docPropStoredField = prop.docPropStoredField;
    }

    public HitPropertyDocumentStoredField(BlackLabIndex index, String fieldName, String friendlyName) {
        super();
        this.fieldName = fieldName;
        this.docPropStoredField = new DocPropertyStoredField(index, fieldName, friendlyName);
    }

    public HitPropertyDocumentStoredField(BlackLabIndex index, String fieldName) {
        this(index, fieldName, fieldName);
    }

    @Override
    public HitProperty copyWith(Hits newHits, Contexts contexts, boolean invert) {
        return new HitPropertyDocumentStoredField(this, newHits, invert);
    }

    @Override
    public PropertyValueString get(int result) {
        return DocPropertyStoredField.fromArray(docPropStoredField.get(hits.hitsArrays().doc(result)));
    }

    @Override
    public int compare(int a, int b) {
        final int docA = hits.hitsArrays().doc(a);
        final int docB = hits.hitsArrays().doc(b);
        int result = docPropStoredField.compare(docA, docB);
        return reverse ? -result : result;
    }

    @Override
    public String name() {
        return "document: " + docPropStoredField.name();
    }

    @Override
    public String serialize() {
        return serializeReverse() + PropertySerializeUtil.combineParts("field", fieldName);
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
        HitPropertyDocumentStoredField other = (HitPropertyDocumentStoredField) obj;
        if (fieldName == null) {
            if (other.fieldName != null)
                return false;
        } else if (!fieldName.equals(other.fieldName))
            return false;
        return true;
    }

    @Override
    public DocProperty docPropsOnly() {
        return reverse ? docPropStoredField.reverse() : docPropStoredField;
    }

    @Override
    public PropertyValue docPropValues(PropertyValue value) {
        return value;
    }
    
    @Override
    public boolean isDocPropOrHitText() {
        return true;
    }
    
    public String fieldName() {
        return fieldName;
    }
    
    @Override
    public ContextSize needsContextSize(BlackLabIndex index) {
        return null;
    }
}

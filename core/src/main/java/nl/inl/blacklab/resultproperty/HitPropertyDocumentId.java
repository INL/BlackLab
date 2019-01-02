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
 * A hit property for grouping per document id.
 * 
 * NOTE: prefer using HitPropertyDoc, which includes the actual 
 * Doc instance. 
 */
public class HitPropertyDocumentId extends HitProperty {

    static HitPropertyDocumentId deserializeProp() {
        return new HitPropertyDocumentId();
    }

    HitPropertyDocumentId(HitPropertyDocumentId prop, Results<Hit> hits, boolean invert) {
        super(prop, hits, null, invert);
    }

    public HitPropertyDocumentId() {
        super();
    }

    @Override
    public HitProperty copyWith(Results<Hit> newHits, Contexts contexts, boolean invert) {
        return new HitPropertyDocumentId(this, newHits, invert);
    }

    @Override
    public PropertyValueInt get(Hit result) {
        return new PropertyValueInt(result.doc());
    }

    @Override
    public String name() {
        return "document: id";
    }

    @Override
    public int compare(Hit a, Hit b) {
        return reverse ? b.doc() - a.doc() : a.doc() - b.doc();
    }

    @Override
    public String serialize() {
        return serializeReverse() + "docid";
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
    
}

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

import org.apache.lucene.search.Query;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.DocResult;

/**
 * For grouping DocResult objects by decade based on a stored field containing a
 * year.
 */
public class DocPropertyId extends DocProperty {

    DocPropertyId(DocPropertyId prop, boolean invert) {
        super(prop, invert);
    }

    public DocPropertyId() {
    }

    @Override
    public PropertyValueInt get(DocResult result) {
        return new PropertyValueInt(result.identity().id());
    }

    /**
     * Compares two docs on this property
     * 
     * @param a first doc
     * @param b second doc
     * @return 0 if equal, negative if a < b, positive if a > b.
     */
    @Override
    public int compare(DocResult a, DocResult b) {
        int idA = a.identity().id();
        int idB = b.identity().id();
        return reverse ? idB - idA : idA - idB;
    }

    @Override
    public String name() {
        return "id";
    }

    public static DocPropertyId deserialize() {
        return new DocPropertyId();
    }

    @Override
    public String serialize() {
        return serializeReverse() + "id";
    }

    @Override
    public DocProperty reverse() {
        return new DocPropertyId(this, true);
    }

    @Override
    public Query query(BlackLabIndex index, PropertyValue value) {
        return null;
    }

}

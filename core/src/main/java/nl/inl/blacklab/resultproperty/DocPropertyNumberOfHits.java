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
 * For grouping DocResult objects on the number of hits. This would put
 * documents with 1 hit in a group, documents with 2 hits in another group, etc.
 */
public class DocPropertyNumberOfHits extends DocProperty {
    public static DocPropertyNumberOfHits deserialize() {
        return new DocPropertyNumberOfHits();
    }
    
    DocPropertyNumberOfHits(DocPropertyNumberOfHits prop, boolean invert) {
        super(prop, invert);
    }
    
    public DocPropertyNumberOfHits() {
        // NOP
    }

    @Override
    protected boolean sortDescendingByDefault() {
        return true;
    }

    @Override
    public PropertyValueInt get(DocResult result) {
        return new PropertyValueInt(result.size());
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
        if (reverse)
            return b.size() - a.size();
        return a.size() - b.size();
    }

    @Override
    public String name() {
        return "number of hits";
    }

    @Override
    public String serialize() {
        return serializeReverse() + "numhits";
    }

    @Override
    public DocProperty reverse() {
        return new DocPropertyNumberOfHits(this, true);
    }

    @Override
    public Query query(BlackLabIndex index, PropertyValue value) {
        return null;
    }

}

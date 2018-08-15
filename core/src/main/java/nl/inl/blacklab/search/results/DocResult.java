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
package nl.inl.blacklab.search.results;

import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.PropertyValueDoc;

/**
 * A document result, containing a Lucene document from the index and a
 * collection of Hit objects.
 */
public class DocResult extends HitGroup {
    
    private float score;

    public DocResult(QueryInfo queryInfo, PropertyValueDoc doc, float score) {
        super(queryInfo, doc);
        this.score = score;
    }

    /**
     * Construct a DocResult.
     *
     * @param doc the Lucene document id
     * @param docHits hits in the document
     */
    public DocResult(PropertyValue doc, Hits docHits) {
        super(doc, docHits);
        this.score = 0.0f;
    }

    public float getScore() {
        return score;
    }
    
    @Override
    public PropertyValueDoc getIdentity() {
        return (PropertyValueDoc)super.getIdentity();
    }
    
}

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

import java.util.List;

import nl.inl.blacklab.resultproperty.PropertyValue;

/**
 * A group of DocResult objects, plus the "group identity". For example, if
 * you're grouping on author name, the group identity might be the string "Harry
 * Mulisch".
 */
public class DocGroup extends Group<DocResult> {
    
    public static DocGroup fromList(QueryInfo queryInfo, PropertyValue groupIdentity, List<DocResult> storedResults, int totalDocuments, long totalTokens) {
        return new DocGroup(queryInfo, groupIdentity, storedResults, totalDocuments, totalTokens);
    }
    
    private long totalTokens;

    private int storedHits;
    
    protected DocGroup(QueryInfo queryInfo, PropertyValue groupIdentity, List<DocResult> storedResults, int totalDocuments, long totalTokens) {
        super(groupIdentity, DocResults.fromList(queryInfo, storedResults, (SampleParameters)null, (WindowStats)null), totalDocuments);
        this.totalTokens = totalTokens;
        storedHits = 0;
        for (DocResult result: storedResults) {
            storedHits += result.numberOfStoredResults();
        }
    }
    
    @Override
    public DocResults storedResults() {
        return (DocResults) super.storedResults();
    }

    public int numberOfStoredHits() {
        return storedHits;
    }

    public long totalTokens() {
        return totalTokens;
    }

}

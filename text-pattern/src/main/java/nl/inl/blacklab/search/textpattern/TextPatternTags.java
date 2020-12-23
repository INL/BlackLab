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
package nl.inl.blacklab.search.textpattern;

import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.index.Term;

import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryTags;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A TextPattern matching a word.
 */
public class TextPatternTags extends TextPattern {

    protected String elementName;

    Map<String, String> attr;

    public TextPatternTags(String elementName, Map<String, String> attr) {
        this.elementName = elementName;
        this.attr = attr;
    }

    public TextPatternTags(String elementName) {
        this(elementName, null);
    }

    public Term getTerm(String fieldName) {
        return new Term(fieldName, elementName);
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) {
        // Desensitize tag name and attribute values if required
        String elementName1 = optInsensitive(context, elementName);
        Map<String, String> attrOptIns = new HashMap<>();
        for (Map.Entry<String, String> e : attr.entrySet()) {
            attrOptIns.put(e.getKey(), optInsensitive(context, e.getValue()));
        }

        // Return the proper SpanQuery depending on index version
        QueryExecutionContext startTagContext = context.withXmlTagsAnnotation();
        String startTagFieldName = startTagContext.luceneField();
        return new SpanQueryTags(QueryInfo.create(context.index(), context.field()), startTagFieldName, elementName1, attrOptIns);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternTags) {
            TextPatternTags tp = ((TextPatternTags) obj);
            return elementName.equals(tp.elementName) && attr.equals(tp.attr);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return elementName.hashCode() + attr.hashCode();
    }

    @Override
    public String toString() {
        if (attr != null && !attr.isEmpty())
            return "TAGS(" + elementName + ", " + attr + ")";
        return "TAGS(" + elementName + ")";
    }

}

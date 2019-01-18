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

import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import nl.inl.blacklab.analysis.BLDutchAnalyzer;
import nl.inl.blacklab.search.results.DocResult;
import nl.inl.util.LuceneUtil;
import nl.inl.util.StringUtil;

/**
 * For grouping DocResult objects by the value of a stored field in the Lucene
 * documents. The field name is given when instantiating this class, and might
 * be "author", "year", and such.
 */
public class DocPropertyStoredField extends DocProperty {
    private String fieldName;
    private String friendlyName;
    
    public DocPropertyStoredField(DocPropertyStoredField prop, boolean invert) {
        super(prop, invert);
        this.fieldName = prop.fieldName;
        this.friendlyName = prop.friendlyName;
    }

    public DocPropertyStoredField(String fieldName) {
        this(fieldName, fieldName);
    }

    public DocPropertyStoredField(String fieldName, String friendlyName) {
        this.fieldName = fieldName;
        this.friendlyName = friendlyName;
    }

    @Override
    public PropertyValueString get(DocResult result) {
        return new PropertyValueString(result.identity().luceneDoc().get(fieldName));
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
        String sa = a.identity().luceneDoc().get(fieldName);
        if (sa == null)
            sa = "";
        String sb = b.identity().luceneDoc().get(fieldName);
        if (sb == null)
            sb = "";
        if (sa.length() == 0) { // sort empty string at the end
            if (sb.length() == 0)
                return 0;
            else
                return reverse ? -1 : 1;
        }
        if (sb.length() == 0) // sort empty string at the end
            return reverse ? 1 : -1;
        return reverse ? sb.compareTo(sa) : sa.compareTo(sb);
    }

    @Override
    public String name() {
        return friendlyName;
    }

    public static DocPropertyStoredField deserialize(String info) {
        return new DocPropertyStoredField(info);
    }

    @Override
    public String serialize() {
        return serializeReverse() + PropertySerializeUtil.combineParts("field", fieldName);
    }

    @Override
    public DocProperty reverse() {
        return new DocPropertyStoredField(this, true);
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
        DocPropertyStoredField other = (DocPropertyStoredField) obj;
        if (fieldName == null) {
            if (other.fieldName != null)
                return false;
        } else if (!fieldName.equals(other.fieldName))
            return false;
        return true;
    }

    @Override
    public Query query(PropertyValue value) {
        if (value.toString().isEmpty())
            return null; // @@@ cannot search for empty string (should use unknown value..?)
        final boolean tokenizeField = false;
        if (tokenizeField) {
            String strValue = "\"" + value.toString().replaceAll("\\\"", "\\\\\"") + "\"";
            try {
                // @@@ NOTE: not all fields are tokenized! Some are lowercased but not tokenized...
                return LuceneUtil.parseLuceneQuery(strValue, new BLDutchAnalyzer(), fieldName); // @@@ analyzer should be customizable
            } catch (ParseException e) {
                return null;
            }
        } else {
            return new TermQuery(new Term(fieldName, StringUtil.stripAccents(value.toString().toLowerCase())));
        }
        //return new TermQuery(new Term(fieldName, strValue));
    }

}

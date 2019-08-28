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

import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexImpl;
import nl.inl.blacklab.search.indexmetadata.FieldType;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.results.DocResult;
import nl.inl.util.LuceneUtil;
import nl.inl.util.StringUtil;

/**
 * For grouping DocResult objects by the value of a stored field in the Lucene
 * documents. The field name is given when instantiating this class, and might
 * be "author", "year", and such.
 */
public class DocPropertyStoredField extends DocProperty {

    /** Lucene field name */
    private String fieldName;

    /** Display name for the field */
    private String friendlyName;

    /** The DocValues per segment (keyed by docBase), or null if we don't have docValues */
    private Map<Integer, SortedDocValues> docValues = null;

    /** Our index */
    private BlackLabIndex index;

    public DocPropertyStoredField(DocPropertyStoredField prop, boolean invert) {
        super(prop, invert);
        this.index = prop.index;
        this.fieldName = prop.fieldName;
        this.friendlyName = prop.friendlyName;
    }

    public DocPropertyStoredField(BlackLabIndex index, String fieldName) {
        this(index, fieldName, fieldName);
    }

    public DocPropertyStoredField(BlackLabIndex index, String fieldName, String friendlyName) {
        this.index = index;
        this.fieldName = fieldName;
        this.friendlyName = friendlyName;

        if (!fieldName.endsWith("Numeric")) { // TODO: use actual data from IndexMetadata
            docValues = new TreeMap<>();
            try {
                if (index.reader() != null) { // skip for MockIndex (testing)
                    for (LeafReaderContext rc : index.reader().leaves()) {
                        LeafReader r = rc.reader();
                        SortedDocValues sortedDocValues = r.getSortedDocValues(fieldName);
                        if (sortedDocValues != null) {
                            docValues.put(rc.docBase, sortedDocValues);
                        }
                    }
                }
                if (docValues.isEmpty()) {
                    // We don't actually have DocValues.
                    docValues = null;
                }
            } catch (IOException e) {
                BlackLabRuntimeException.wrap(e);
            }
        }
    }

    public String get(int docId) {
        if  (docValues != null) {
            // Find the fiid in the correct segment
            Entry<Integer, SortedDocValues> prev = null;
            for (Entry<Integer, SortedDocValues> e : docValues.entrySet()) {
                Integer docBase = e.getKey();
                if (docBase > docId) {
                    // Previous segment (the highest docBase lower than docId) is the right one
                    Integer prevDocBase = prev.getKey();
                    SortedDocValues prevDocValues = prev.getValue();
                    return prevDocValues.get(docId - prevDocBase).utf8ToString();
                }
                prev = e;
            }
            // Last segment is the right one
            Integer prevDocBase = prev.getKey();
            SortedDocValues prevDocValues = prev.getValue();
            return prevDocValues.get(docId - prevDocBase).utf8ToString();
        }
        // We don't have DocValues; just get the property from the document.
        try {
            String value = index.reader().document(docId).get(fieldName);
            return value != null ? value : "";
        } catch (IOException e) {
            throw new BlackLabRuntimeException("Could not fetch document " + docId, e);
        }
    }

    public String get(PropertyValueDoc doc) {
        if (doc.value().isLuceneDocCached()) {
            // We have the Document already, get the property from there
            return doc.luceneDoc().get(fieldName);
        }
        return get(doc.id());
    }

    @Override
    public PropertyValueString get(DocResult result) {
        return new PropertyValueString(get(result.identity()));
    }

    /**
     * Compares two docs on this property
     *
     * @param docId1 first doc
     * @param docId2 second doc
     * @return 0 if equal, negative if a < b, positive if a > b.
     */
    public int compare(int docId1, int docId2) {
        String sa = get(docId1);
        String sb = get(docId2);
        if (sa.isEmpty()) { // sort empty string at the end
            if (sb.isEmpty())
                return 0;
            else
                return reverse ? -1 : 1;
        }
        if (sb.isEmpty()) // sort empty string at the end
            return reverse ? 1 : -1;
        return reverse ? PropertyValueString.collator.compare(sb, sa) : PropertyValueString.collator.compare(sa, sb);
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
        return reverse ? PropertyValue.collator.compare(sb, sa) : PropertyValue.collator.compare(sa, sb);
    }

    @Override
    public String name() {
        return friendlyName;
    }

    public static DocPropertyStoredField deserialize(BlackLabIndex index, String info) {
        return new DocPropertyStoredField(index, PropertySerializeUtil.unescapePart(info));
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
    public Query query(BlackLabIndex index, PropertyValue value) {
        MetadataField metadataField = index.metadataField(fieldName);
        if (value.toString().isEmpty())
            return null; // Cannot search for empty string (to avoid this problem, configure ans "Unknown value")
        if (!value.toString().isEmpty() && metadataField.type() == FieldType.TOKENIZED) {
            String strValue = "\"" + value.toString().replaceAll("\\\"", "\\\\\"") + "\"";
            try {
                Analyzer analyzer = BlackLabIndexImpl.analyzerInstance(metadataField.analyzerName());
                return LuceneUtil.parseLuceneQuery(strValue, analyzer, fieldName);
            } catch (ParseException e) {
                return null;
            }
        } else {
            return new TermQuery(new Term(fieldName, StringUtil.stripAccents(value.toString().toLowerCase())));
        }
        //return new TermQuery(new Term(fieldName, strValue));
    }

    @Override
    public boolean canConstructQuery(BlackLabIndex index, PropertyValue value) {
        return !value.toString().isEmpty();
    }

}

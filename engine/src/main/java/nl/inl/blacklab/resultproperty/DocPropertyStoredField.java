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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;

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
    private Map<Integer, SortedSetDocValues> docValues = null;

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
                        SortedSetDocValues sortedDocValues = r.getSortedSetDocValues(fieldName);
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

    /**
     * Get the raw values straight from lucene.
     * The returned array is in whichever order the values were originally added to the document.
     *
     * @param docId
     * @return
     */
    public String[] get(int docId) {
        if  (docValues != null) {
            // Find the fiid in the correct segment
            Entry<Integer, SortedSetDocValues> target = null;
            for (Entry<Integer, SortedSetDocValues> e : this.docValues.entrySet()) {
                if (e.getKey() > docId) { break; }
                target = e;
            }

            final List<String> ret = new ArrayList<>();
            if (target != null) {
                final Integer targetDocBase = target.getKey();
                final SortedSetDocValues targetDocValues = target.getValue();
                targetDocValues.setDocument(docId - targetDocBase);
                for (long ord = targetDocValues.nextOrd(); ord != SortedSetDocValues.NO_MORE_ORDS; ord = targetDocValues.nextOrd()) {
                    BytesRef val = targetDocValues.lookupOrd(ord);
                    ret.add(new String(val.bytes, val.offset, val.length, StandardCharsets.UTF_8));
                }
            }
            return ret.toArray(new String[ret.size()]);
        }
        // We don't have DocValues; just get the property from the document.
        try {
            return index.reader().document(docId).getValues(fieldName);
        } catch (IOException e) {
            throw new BlackLabRuntimeException("Could not fetch document " + docId, e);
        }
    }

    /**
     * Get the raw values straight from lucene.
     * The returned array is in whichever order the values were originally added to the document.
     *
     * @param docId
     * @return
     */
    public String[] get(PropertyValueDoc doc) {
        // We have the Document already, get the property from there
        if (doc.value().isLuceneDocCached()) {
            return doc.luceneDoc().getValues(fieldName);
        }
        return get(doc.id());
    }

    /** Get the values as PropertyValue. */
    @Override
    public PropertyValueString get(DocResult result) {
        String[] values = get(result.identity());
        return fromArray(values);
    }

    /** Get the first value. The empty string is returned if there are no values for this document */
    public String getFirstValue(DocResult result) {
        return getFirstValue(result.identity());
    }

    /** Get the first value. The empty string is returned if there are no values for this document */
    public String getFirstValue(PropertyValueDoc doc) {
        return getFirstValue(doc.id());
    }

    /** Get the first value. The empty string is returned if there are no values for this document */
    public String getFirstValue(int docId) {
        String[] values = get(docId);
        return values.length > 0 ? values[0] : "";
    }

    /** Convert an array of string values to a PropertyValueString. */
    public static PropertyValueString fromArray(String[] values) {
        return new PropertyValueString(StringUtils.join(values, " · "));
    }

    /**
     * Compares two docs on this property
     *
     * @param docId1 first doc
     * @param docId2 second doc
     * @return 0 if equal, negative if a < b, positive if a > b.
     */
    public int compare(int docId1, int docId2) {
        return fromArray(get(docId1)).compareTo(fromArray(get(docId2))) * (reverse ? -1 : 1);
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
        PropertyValue v1 = get(a);
        PropertyValue v2 = get(b);
        return v1.compareTo(v2) * (reverse ? -1 : 1);
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

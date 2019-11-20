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

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.search.Query;
import org.apache.lucene.uninverting.UninvertingReader;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.results.DocResult;

/**
 * Retrieves the length of an annotated field (i.e. the main "contents" field) in
 * tokens.
 */
public class DocPropertyAnnotatedFieldLength extends DocProperty {

    public static DocPropertyAnnotatedFieldLength deserialize(BlackLabIndex index, String info) {
        return new DocPropertyAnnotatedFieldLength(index, PropertySerializeUtil.unescapePart(info));
    }

    private String fieldName;
    
    private String friendlyName;

    /** The DocValues per segment (keyed by docBase), or null if we don't have docValues */
    private Map<Integer, NumericDocValues> docValues = null;
    
    private BlackLabIndex index;

    DocPropertyAnnotatedFieldLength(DocPropertyAnnotatedFieldLength prop, boolean invert) {
        super(prop, invert);
        index = prop.index;
        fieldName = prop.fieldName;
        friendlyName = prop.friendlyName;
    }

    public DocPropertyAnnotatedFieldLength(BlackLabIndex index, String fieldName, String friendlyName) {
        this.index = index;
        this.fieldName = AnnotatedFieldNameUtil.lengthTokensField(fieldName);
        this.friendlyName = friendlyName;
        docValues = new TreeMap<>();
        try {
            for (LeafReaderContext rc : index.reader().leaves()) {
                LeafReader r = rc.reader();
                NumericDocValues numericDocValues = r.getNumericDocValues(fieldName);
                if (numericDocValues == null) {
                    // Use UninvertingReader to simulate DocValues (slower)
                    Map<String, UninvertingReader.Type> fields = new TreeMap<>();
                    fields.put(fieldName, UninvertingReader.Type.INTEGER);
                    @SuppressWarnings("resource")
                    UninvertingReader uninv = new UninvertingReader(r, fields);
                    numericDocValues = uninv.getNumericDocValues(fieldName);
                }
                if (numericDocValues != null) {
                    docValues.put(rc.docBase, numericDocValues);
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

    public DocPropertyAnnotatedFieldLength(BlackLabIndex index, String fieldName) {
        this(index, fieldName, fieldName + " length");
    }

    public long get(int docId) {
        long subtractClosingToken = 1;
        if (docValues != null) {
            // Find the fiid in the correct segment
            Entry<Integer, NumericDocValues> prev = null;
            for (Entry<Integer, NumericDocValues> e : docValues.entrySet()) {
                Integer docBase = e.getKey();
                if (docBase > docId) {
                    // Previous segment (the highest docBase lower than docId) is the right one
                    Integer prevDocBase = prev.getKey();
                    NumericDocValues prevDocValues = prev.getValue();
                    return prevDocValues.get(docId - prevDocBase) - subtractClosingToken;
                }
                prev = e;
            }
            // Last segment is the right one
            Integer prevDocBase = prev.getKey();
            NumericDocValues prevDocValues = prev.getValue();
            return prevDocValues.get(docId - prevDocBase) - subtractClosingToken;
        }
        
        // Not cached; find fiid by reading stored value from Document now
        try {
            return Long.parseLong(index.reader().document(docId).get(fieldName)) - subtractClosingToken;
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    private long get(PropertyValueDoc identity) {
        if (identity.value().isLuceneDocCached()) {
            // if we already have the document, get the value from there
            long subtractClosingToken = 1;
            return Long.parseLong(identity.luceneDoc().get(fieldName)) - subtractClosingToken;
        } else
            return get(identity.id());
    }

    @Override
    public PropertyValueInt get(DocResult result) {
        try {
            long length = get(result.identity());
            return new PropertyValueInt(length);
        } catch (NumberFormatException e) {
            return new PropertyValueInt(0);
        }
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
        try {
            int ia = Integer.parseInt(a.identity().luceneDoc().get(fieldName));
            int ib = Integer.parseInt(b.identity().luceneDoc().get(fieldName));
            return reverse ? ib - ia : ia - ib;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public String name() {
        return friendlyName;
    }

    @Override
    public String serialize() {
        return serializeReverse() + PropertySerializeUtil.combineParts("fieldlen", fieldName);
    }

    @Override
    public DocProperty reverse() {
        return new DocPropertyAnnotatedFieldLength(this, true);
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
        DocPropertyAnnotatedFieldLength other = (DocPropertyAnnotatedFieldLength) obj;
        if (fieldName == null) {
            if (other.fieldName != null)
                return false;
        } else if (!fieldName.equals(other.fieldName))
            return false;
        return true;
    }

    @Override
    public Query query(BlackLabIndex index, PropertyValue value) {
        return null;
    }

}

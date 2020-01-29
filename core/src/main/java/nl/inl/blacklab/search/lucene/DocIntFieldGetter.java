package nl.inl.blacklab.search.lucene;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.uninverting.UninvertingReader;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

/**
 * Used to get an integer field value for a document.
 *
 * This is used by SpanQueryFiSeq to get the forward index id (fiid).
 */
public class DocIntFieldGetter {

    /** The Lucene index reader, for querying field length */
    private LeafReader reader;

    /** Field name to check for the length of the field in tokens */
    private String intFieldName;

    public DocIntFieldGetter(LeafReader reader, String fieldName) {
        this.reader = reader;
        intFieldName = fieldName;
    }

    /**
     * Get the value of our field in the specified document.
     *
     * @param doc the document
     * @return value of the int field
     */
    public synchronized int getFieldValue(int doc) {
        try {
            NumericDocValues docValues = reader.getNumericDocValues(intFieldName);
            if (docValues != null) {
                return (int)docValues.get(doc);
            }
        } catch (IOException ex) {
            throw new BlackLabRuntimeException("Error getting NumericDocValues for " + intFieldName, ex);
        }

        throw new BlackLabRuntimeException("Can't find " + intFieldName + " for doc " + doc);
    }
}

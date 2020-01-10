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
public class DocIntFieldGetter implements Closeable {

    /** The Lucene index reader, for querying field length */
    private LeafReader reader;

    /** Field name to check for the length of the field in tokens */
    private String intFieldName;

    /** Lengths may have been cached using FieldCache */
    private NumericDocValues docValues;

    /** Reader for getting docValues even when they weren't explicitly indexed */
    private UninvertingReader uninv;

    public DocIntFieldGetter(LeafReader reader, String fieldName) {
        this.reader = reader;
        intFieldName = fieldName;

        // Cache the lengths for this field to speed things up
        try {
            docValues = reader.getNumericDocValues(intFieldName);
            if (docValues == null) {
                // Use UninvertingReader to simulate DocValues (slower)
                Map<String, UninvertingReader.Type> fields = new TreeMap<>();
                fields.put(intFieldName, UninvertingReader.Type.INTEGER);
                @SuppressWarnings("resource")
                UninvertingReader uninv = new UninvertingReader(reader, fields);
                docValues = uninv.getNumericDocValues(intFieldName);
            }
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public void close() {
        if (uninv != null) {
            try {
                uninv.close();
            } catch (IOException e) {
                throw BlackLabRuntimeException.wrap(e);
            }
        }
    }

    /**
     * Get the value of our field in the specified document.
     *
     * @param doc the document
     * @return value of the int field
     */
    public synchronized int getFieldValue(int doc) {

        // Cached doc values?
        if (docValues != null) {
            return (int) docValues.get(doc);
        }

        // No; get the field value from the Document object.
        // (Note that this code should never be executed, but just to be safe)
        Document document;
        try {
            document = reader.document(doc);
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
        String strVal = document.get(intFieldName);
        if (strVal != null) {
            // Yes, found the field length stored in the index.
            // Parse and return it.
            return Integer.parseInt(strVal);
        }
        throw new BlackLabRuntimeException("Can't find " + intFieldName + " for doc " + doc);
    }
}

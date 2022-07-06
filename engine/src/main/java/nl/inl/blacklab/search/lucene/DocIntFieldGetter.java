package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.solr.uninverting.UninvertingReader;

import net.jcip.annotations.NotThreadSafe;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

/**
 * Used to get an integer field value for a document.
 *
 * This is used by SpanQueryFiSeq to get the forward index id (fiid).
 *
 * Not thread-safe (because of DocValues; but only used from Spans).
 *
 * CAUTION: the advance() method can only be called with ascending doc ids!
 */
@NotThreadSafe
public class DocIntFieldGetter implements AutoCloseable {

    /** The Lucene index reader, for querying field length */
    private final LeafReader reader;

    /** Field name to check for the length of the field in tokens */
    private final String intFieldName;

    /** Lengths may have been cached using FieldCache */
    private NumericDocValues docValues;

    public DocIntFieldGetter(LeafReader reader, String fieldName) {
        this.reader = reader;
        intFieldName = fieldName;

        // Cache the lengths for this field to speed things up
        try {
            docValues = reader.getNumericDocValues(intFieldName);
            if (docValues == null) {
                // Use UninvertingReader to simulate DocValues (slower)
                Map<String, UninvertingReader.Type> fields = new TreeMap<>();
                fields.put(intFieldName, UninvertingReader.Type.INTEGER_POINT);
                LeafReader uninv = UninvertingReader.wrap(reader, fields::get);
                docValues = uninv.getNumericDocValues(intFieldName);
            }
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public void close() {
        // NOP
    }

    /**
     * Get the value of our field in the specified document.
     *
     * @param doc the document
     * @return value of the int field
     */
    public synchronized int advance(int doc) {

        // Cached doc values?
        if (docValues != null) {
        	try {
        		docValues.advanceExact(doc);
				return (int)docValues.longValue();
			} catch (IOException e) {
                throw BlackLabRuntimeException.wrap(e);
			}
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

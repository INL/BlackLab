package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DocumentStoredFieldVisitor;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;

/**
 * Used to get the field length in tokens for a document.
 *
 * This is used by SpanQueryNot and SpanQueryExpansion to make sure we don't go
 * beyond the document end.
 *
 * This class is instantiated and used by a single Spans to get lengths for a single
 * index segment. It does not need to be thread-safe.
 */
public class DocFieldLengthGetter {

    /** The Lucene index reader, for querying field length */
    private final LeafReader reader;

    /**
     * For testing, we don't have an IndexReader available, so we use test values
     */
    private boolean useTestValues = false;

    /** Did we check if the field length is stored separately in the index? */
    private boolean lookedForLengthField = false;

    /** Is the field length stored separately in the index? */
    private boolean lengthFieldIsStored = false;

    /** Field name to check for the length of the field in tokens */
    private final String lengthTokensFieldName;

    /** Field visitor to get the field value (without loading the entire document) */
    private final DocumentStoredFieldVisitor lengthTokensFieldVisitor;

    /** Lengths may have been cached using FieldCache */
    private final NumericDocValues cachedFieldLengths;

    public DocFieldLengthGetter(LeafReader reader, String fieldName) {
        this.reader = reader;
        lengthTokensFieldName = AnnotatedFieldNameUtil.lengthTokensField(fieldName);
        lengthTokensFieldVisitor = new DocumentStoredFieldVisitor(lengthTokensFieldName);

        // Cache the lengths to speed things up
        if (reader != null) {
            try {
                cachedFieldLengths = reader.getNumericDocValues(lengthTokensFieldName);
                if (cachedFieldLengths == null)
                    throw new BlackLabRuntimeException("No DocValues for field " + lengthTokensFieldName);
            } catch (IOException e) {
                throw BlackLabRuntimeException.wrap(e);
            }
        } else {
            // Only used for test (all lengths are the same)
            cachedFieldLengths = null;
        }
    }

    /**
     * For testing, we don't have an IndexReader available, so we use test values.
     *
     * The test values are: there are 3 documents (0, 1 and 2) and each is 5 tokens
     * long.
     *
     * @param test whether or not we want to use test values
     */
    public void setTest(boolean test) {
        this.useTestValues = test;
    }

    /**
     * Get the number of indexed tokens for our field in the specified document.
     *
     * Used to produce all tokens that aren't hits in our clause.
     *
     * NOTE: this includes the "extra closing token" at the end that may contain punctuation
     * after the last word! You must subtract 1 for indices that have this extra closing
     * token (all recent indices do).
     *
     * @param doc the document
     * @return the number of tokens
     */
    public int getFieldLength(int doc) {

        if (useTestValues)
            return 6; // while testing, all documents have same length

        try {
            if (cachedFieldLengths.advanceExact(doc)){
                return (int)cachedFieldLengths.longValue();
            }
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }

        if (!lookedForLengthField || lengthFieldIsStored) {
            // We either know the field length is stored in the index,
            // or we haven't checked yet and should do so now.
            try {
                reader.document(doc, lengthTokensFieldVisitor);
                Document document = lengthTokensFieldVisitor.getDocument();
                String strLength = document.get(lengthTokensFieldName);
                lookedForLengthField = true;
                if (strLength != null) {
                    // Yes, found the field length stored in the index.
                    // Parse and return it.
                    lengthFieldIsStored = true;
                    return Integer.parseInt(strLength);
                }
                // No, length field is not stored in the index. Always use term vector from now on.
                lengthFieldIsStored = false;
            } catch (IOException e) {
                throw BlackLabRuntimeException.wrap(e);
            }
        }

        throw new BlackLabRuntimeException("Could not get field length for document " + doc);
    }
}

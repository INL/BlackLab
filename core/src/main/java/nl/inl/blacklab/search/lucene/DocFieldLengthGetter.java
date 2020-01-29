package nl.inl.blacklab.search.lucene;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.uninverting.UninvertingReader;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;

/**
 * Used to get the field length in tokens for a document.
 *
 * This is used by SpanQueryNot and SpanQueryExpansion to make sure we don't go
 * beyond the document end.
 */
class DocFieldLengthGetter {
    /**
     * We check some cache entries to see if document lengths were saved in the
     * index or not. (These days, they should always be saved, but we do this in
     * case someone uses an old index)
     */
    private static final int NUMBER_OF_CACHE_ENTRIES_TO_CHECK = 1000;

    /** The Lucene index reader, for querying field length */
    private LeafReader reader;

    /**
     * For testing, we don't have an IndexReader available, so we use test values
     */
    private boolean useTestValues = false;

    /** Did we check if the field length is stored separately in the index? */
    private boolean lookedForLengthField = false;

    /** Is the field length stored separately in the index? */
    private boolean lengthFieldIsStored = false;

    /** Name of the field we're searching */
    private String fieldName;

    /** Field name to check for the length of the field in tokens */
    private String lengthTokensFieldName;

    public DocFieldLengthGetter(LeafReader reader, String fieldName) {
        this.reader = reader;
        this.fieldName = fieldName;
        lengthTokensFieldName = AnnotatedFieldNameUtil.lengthTokensField(fieldName);
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
     * NOTE: this includes the "dummy" token at the end that may contain punctuation
     * after the last word! You must subtract 1 for indices that have these dummy
     * tokens (all recent indices do).
     *
     * @param doc the document
     * @return the number of tokens
     */
    public int getFieldLength(int doc) {

        if (useTestValues)
            return 6; // while testing, all documents have same length

        try {
            NumericDocValues docValues = reader.getNumericDocValues(lengthTokensFieldName);
            if (docValues != null) {
                return (int)docValues.get(doc);
            }
        } catch (IOException ex) {
            throw new BlackLabRuntimeException("Error getting NumericDocValues for " + lengthTokensFieldName, ex);
        }

        throw new BlackLabRuntimeException("Can't find " + lengthTokensFieldName + " for doc " + doc);
    }
}

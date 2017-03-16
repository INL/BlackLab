package nl.inl.blacklab.search.lucene;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.uninverting.UninvertingReader;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.Searcher;

/**
 * Used to get the field length in tokens for a document.
 *
 * This is used by SpanQueryNot and SpanQueryExpansion to make sure
 * we don't go beyond the document end.
 */
public class DocFieldLengthGetter implements Closeable {
	/** We check some cache entries to see if document lengths were saved in the index or not.
	 * (These days, they should always be saved, but we do this in case someone uses an old index)
	 */
	private static final int NUMBER_OF_CACHE_ENTRIES_TO_CHECK = 1000;

	/** The Lucene index reader, for querying field length */
	private LeafReader reader;

	/** For testing, we don't have an IndexReader available, so we use test values */
	private boolean useTestValues = false;

	/** Did we check if the field length is stored separately in the index? */
	private boolean lookedForLengthField = false;

	/** Is the field length stored separately in the index? */
	private boolean lengthFieldIsStored = false;

	/** Name of the field we're searching */
	private String fieldName;

	/** Field name to check for the length of the field in tokens */
	private String lengthTokensFieldName;

	/** Lengths may have been cached using FieldCache */
	private NumericDocValues cachedFieldLengths;

	private UninvertingReader uninv;

	public DocFieldLengthGetter(LeafReader reader, String fieldName) {
		this.reader = reader;
		this.fieldName = fieldName;
		lengthTokensFieldName = ComplexFieldUtil.lengthTokensField(fieldName);

		if (fieldName.equals(Searcher.DEFAULT_CONTENTS_FIELD_NAME)) {
			// Cache the lengths for this field to speed things up
			try {
				// NOTE: UninvertingReader is an IndexReader that can get docValues even when they weren't explicitly indexed
				Map<String, UninvertingReader.Type> fields = new HashMap<>();
				fields.put(lengthTokensFieldName, UninvertingReader.Type.INTEGER);
				uninv = new UninvertingReader(reader, fields);
				cachedFieldLengths = uninv.getNumericDocValues(lengthTokensFieldName); //FieldCache.DEFAULT.getInts(reader, lengthTokensFieldName, true);

				// Check if the cache was retrieved OK
				boolean allZeroes = true;
				int numToCheck = Math.min(NUMBER_OF_CACHE_ENTRIES_TO_CHECK, reader.maxDoc());
				for (int i = 0; i < numToCheck ; i++) {
					// (NOTE: we don't check if document wasn't deleted, but that shouldn't matter here)
					if (cachedFieldLengths.get(i) != 0) {
						allZeroes = false;
						break;
					}
				}
				if (allZeroes) {
					// Tokens lengths weren't saved in the index, skip cache
					cachedFieldLengths = null;
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	@Override
	public void close() {
		if (uninv != null) {
			try {
				uninv.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	/** For testing, we don't have an IndexReader available, so we use test values.
	 *
	 *  The test values are: there are 3 documents (0, 1 and 2) and each is 5 tokens long.
	 *
	 *  @param test whether or not we want to use test values
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
			return 5; // while testing, all documents have same length

		if (cachedFieldLengths != null) {
			return (int)cachedFieldLengths.get(doc);
		}

		if (!lookedForLengthField || lengthFieldIsStored)  {
			// We either know the field length is stored in the index,
			// or we haven't checked yet and should do so now.
			try {
				Document document = reader.document(doc);
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
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		// Calculate the total field length by adding all the term frequencies.
		// (much slower)
		try {
			Terms vector = reader.getTermVector(doc, fieldName);
			TermsEnum termsEnum = vector.iterator();
			int termFreq = 0;
			while (termsEnum.next() != null) {
			    termFreq += termsEnum.totalTermFreq();
			}
			return termFreq;

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
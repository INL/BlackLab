package nl.inl.blacklab.forwardindex;

import java.io.File;
import java.nio.charset.Charset;

import org.eclipse.collections.api.set.primitive.MutableIntSet;

/**
 * Keeps a list of unique terms and their sort positions.
 */
public abstract class Terms {

	public static final int NO_TERM = -1;

	static final Charset DEFAULT_CHARSET = Charset.forName("utf-8");

	/**
	 * Get the existing index number of a term, or add it to the term list
	 * and assign it a new index number.
	 *
	 * In index mode, this is fast. In search mode, this is slower, because
	 * we have to do a binary search through the memory-mapped terms file.
	 * However, this is only done rarely.
	 *
	 * If you care about this being fast, call
	 * buildTermIndex() at the start of your application.
	 *
	 * @param term the term to get the index number for
	 * @return the term's index number
	 */
	public abstract int indexOf(String term);

	/**
	 * Get the index number(s) of terms matching a string.
	 *
	 * This is used in search mode when translating queries into NFAs. Depending on
	 * case-sensitivity settings, a single term string may match multiple terms.
	 *
	 * @param results (out) index numbers for the matching term(s)
	 * @param term the term to get the index number for
	 * @param caseSensitive compare case-sensitively? (currently switches both case- and diacritics-sensitivity)
	 * @param diacSensitive compare diacritics-sensitively? (currently ignored)
	 */
	public abstract void indexOf(MutableIntSet results, String term, boolean caseSensitive, boolean diacSensitive);

	/**
	 * Build the index from term to term id. Depending on the terms
	 * implementation, this may speed up the first call to indexOf().
	 */
	public void buildTermIndex() {
		// May be implemented by child class
	}

	/**
	 * Clear the Terms object.
	 */
	public abstract void clear();

	/**
	 * Write the terms file
	 * @param termsFile where to write the terms file
	 */
	public abstract void write(File termsFile);

	/**
	 * Get a term by id. Only works in search mode.
	 * @param id the term id
	 * @return the corresponding term
	 */
	public abstract String get(Integer id);

	/**
	 * @return the number of terms in this object
	 */
	public abstract int numberOfTerms();

	/**
	 * Get the sort position for a term based on its term id
	 * @param id the term id
	 * @param sensitive whether we want the sensitive or insensitive sort position
	 * @return the sort position
	 */
	public abstract int idToSortPosition(int id, boolean sensitive);

	/**
	 * Convert an array of term ids to sort positions
	 * @param termId the term ids
	 * @param sortOrder the sort positions
	 * @param sensitive whether we want the sensitive or insensitive sort positions
	 */
	public void toSortOrder(int[] termId, int[] sortOrder, boolean sensitive) {
		for (int i = 0; i < termId.length; i++) {
			sortOrder[i] = idToSortPosition(termId[i], sensitive);
		}
	}

	/**
	 * Compare two terms (from their term ids) based on their sort positions
	 * @param termId1 id of the first term
	 * @param termId2 id of the second term
	 * @param sensitive whether we want to compare sensitively or insensitively
	 * @return the comparison result (negative if term1 < term2, zero if equal, positive if term1 > term2)
	 */
	public int compareSortPosition(int termId1, int termId2, boolean sensitive) {
		return idToSortPosition(termId1, sensitive) - idToSortPosition(termId2, sensitive);
	}

	protected abstract void setBlockBasedFile(boolean useBlockBasedTermsFile);

	public static Terms open(boolean indexMode, Collators collators, File termsFile, boolean useBlockBasedTermsFile) {
		return new TermsImplV3(indexMode, collators, termsFile, useBlockBasedTermsFile);
	}

	public abstract boolean termsEqual(int[] termId, boolean caseSensitive, boolean diacSensitive);

    public int deserializeToken(String term) {
        int termId;
        if (term.equals("~"))
            termId = Terms.NO_TERM; // no token, effectively a "null" value
        else {
            if (term.startsWith("~~")) {
                // tilde in first position has to be escaped
                // because of how null value is encoded
                term = term.substring(1);
            }
            termId = indexOf(term);
        }
        return termId;
    }

    public String serializeTerm(int valueTokenId) {
        String token;
        if (valueTokenId < 0)
            token = "~"; // no token, effectively a "null" value
        else {
            token = get(valueTokenId);
            if (token.length() > 0 && token.charAt(0) == '~') {
                // tilde in first position has to be escaped
                // because of how null value is encoded
                token = "~" + token;
            }
        }
        return token;
    }

}
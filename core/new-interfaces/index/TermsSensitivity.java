package nl.inl.blacklab.interfaces.index;

import java.util.Iterator;
import java.util.stream.Stream;

import org.eclipse.collections.api.set.primitive.MutableIntSet;

import nl.inl.blacklab.interfaces.PrimitiveIntComparator;

/**
 * Keeps a list of unique terms and their sort positions, and provides
 * a view with a single matching sensitivity (sensitive or insensitive,
 * with diac/case-sensitivity separate in the future)
 */
public interface TermsSensitivity extends Iterable<String> {
	
	/**
	 * Get the index number(s) of terms matching a string.
	 *
	 * This is used in search mode when translating queries into NFAs. Depending on
	 * case-sensitivity settings, a single term string may match multiple terms.
	 *
	 * @param results (out) index numbers for the matching term(s)
	 * @param term the term to get the index number for
	 */
	void indexOf(MutableIntSet results, String term);

	/**
	 * Get a term by id. Only works in search mode.
	 * @param id the term id
	 * @return the corresponding term
	 */
	String get(Integer id);

	/**
	 * Iterate over all terms. Only works in search mode.
	 * @return stream of terms
	 */
	@Override
	Iterator<String> iterator();

	/**
	 * Stream over all terms. Only works in search mode.
	 * @return stream of terms
	 */
	Stream<String> stream();

	/**
	 * Get the sort position for a term based on its term id
	 * @param id the term id
	 * @param sensitivity whether we want the sensitive or insensitive sort position
	 * @return the sort position
	 */
	int idToSortPosition(int id);

	/**
	 * Convert an array of term ids to sort positions
	 * @param termId the term ids
	 * @param sortOrder the sort positions
	 * @param sensitivity whether we want the sensitive or insensitive sort positions
	 */
	default void toSortOrder(int[] termId, int[] sortOrder) {
		for (int i = 0; i < termId.length; i++) {
			sortOrder[i] = idToSortPosition(termId[i]);
		}
	}
	
	/**
	 * Returns a comparator for comparing two terms (from their term ids) based on their sort positions
	 * @return comparator
	 */
	PrimitiveIntComparator sortPositionComparator();

	boolean termsEqual(int[] termId);

    String serializeTerm(int termId);

    int deserializeToken(String term);

}

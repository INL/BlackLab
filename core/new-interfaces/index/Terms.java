package nl.inl.blacklab.interfaces.index;

import java.io.File;
import java.util.Iterator;
import java.util.stream.Stream;

import nl.inl.blacklab.interfaces.MatchSensitivity;

/**
 * Keeps a list of unique terms and their sort positions.
 */
interface Terms extends Iterable<String> {
	
	/**
	 * "no term at this position" value (kind of like null)
	 */
	int NO_TERM = -1;

	/**
	 * Clear the Terms object.
	 */
	void clear();
	
	/**
	 * Get a view on this Terms object with a fixed sensitivity setting.
	 * 
	 * @param sensitivity desired sensitivity setting
	 * @return view
	 */
	TermsSensitivity sensitivity(MatchSensitivity sensitivity);

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
	int indexOf(String term);

	/**
	 * Write the terms file
	 * @param termsFile where to write the terms file
	 */
	void write(File termsFile);

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

    String serializeTerm(int termId);

    int deserializeToken(String term);

}

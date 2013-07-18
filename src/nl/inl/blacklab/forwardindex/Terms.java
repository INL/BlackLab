package nl.inl.blacklab.forwardindex;

import java.io.File;

public abstract class Terms {

	/**
	 * Get the existing index number of a term, or add it to the term list
	 * and assign it a new index number.
	 *
	 * Can only be called in indexMode right now.
	 *
	 * @param term the term to get the index number for
	 * @return the term's index number
	 */
	public abstract int indexOf(String term);

	public abstract void clear();

	public abstract void write(File termsFile);

	public abstract String get(Integer integer);

	public abstract int sortPositionToId(int sortPosition);

	public abstract int idToSortPosition(int id);

	public abstract int sortPositionToIdInsensitive(int sortPosition);

	public abstract int idToSortPositionInsensitive(int id);

	public abstract String getFromSortPosition(int sortPosition);

	public abstract int numberOfTerms();

}
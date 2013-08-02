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

	public abstract int numberOfTerms();

	public abstract int idToSortPosition(int id, boolean sensitive);

	public void toSortOrder(int[] tokenId, int[] sortOrder, boolean sensitive) {
		for (int i = 0; i < tokenId.length; i++) {
			sortOrder[i] = idToSortPosition(tokenId[i], sensitive);
		}
	}

	public int compareSortPosition(int tokenId1, int tokenId2, boolean sensitive) {
		return idToSortPosition(tokenId1, sensitive) - idToSortPosition(tokenId2, sensitive);
	}

}
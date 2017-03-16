package nl.inl.blacklab.search.fimatch;

import nl.inl.blacklab.search.Searcher;

/**
 * Maps property name to property number, term string to term number,
 * and allows you to instantiante a token source for matching from a
 * certain position in a document.
 */
public abstract class TokenPropMapper {

	public static TokenPropMapper fromSearcher(Searcher searcher, String searchField) {
		return new TokenPropMapperImpl(searcher, searchField);
	}

	public TokenPropMapper() {
		super();
	}

	/**
	 * Get the index number corresponding to the given property name.
	 *
	 * @param propertyName property to get the index for
	 * @return index for this property
	 */
	public abstract int getPropertyNumber(String propertyName);

	/**
	 * Get the term number for a given term string.
	 *
	 * @param propertyNumber which property to get term number for
	 * @param propertyValue which term string to get term number for
	 * @return term number for this term in this property
	 *
	 */
	public abstract int getTermNumber(int propertyNumber, String propertyValue);

	/**
	 * Get a token source.
	 * @param fiid document (forward index) id
	 * @return the token source
	 */
	public abstract TokenSource tokenSource(int fiid);

}
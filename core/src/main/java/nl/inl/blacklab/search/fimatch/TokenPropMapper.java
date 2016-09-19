package nl.inl.blacklab.search.fimatch;

/**
 * Maps property name to property number, term string to term number,
 * and allows you to instantiante a token source for matching from a
 * certain position in a document.
 */
public abstract class TokenPropMapper {

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
	 * @param startingPosition first token the TokenSource should produce
	 * @param direction 1 for a left-to-right TokenSource, -1 for a right-to-left one
	 * @return the token source
	 */
	public abstract TokenSource tokenSource(int fiid, int startingPosition, int direction);

}
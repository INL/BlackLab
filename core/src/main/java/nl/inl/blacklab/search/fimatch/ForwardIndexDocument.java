package nl.inl.blacklab.search.fimatch;

/** Source of tokens for the forward index matching process. */
public abstract class ForwardIndexDocument {

	/** Return token at specified position.
	 *
	 * @param propIndex property to read
	 * @param pos position to read
	 * @return token at this position
	 */
	public abstract int getToken(int propIndex, int pos);

	/**
	 * Return string for term id
	 * @param propIndex property for which we want a term string
	 * @param termId term id
	 * @return corresponding term string
	 */
	public abstract String getTermString(int propIndex, int termId);

}
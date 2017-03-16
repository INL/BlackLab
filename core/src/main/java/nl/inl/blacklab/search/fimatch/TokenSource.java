package nl.inl.blacklab.search.fimatch;

/** Source of tokens for the forward index matching process. */
public abstract class TokenSource {

	/** Return token at specified position.
	 *
	 * @param propIndex property to read
	 * @param pos position to read
	 * @return token at this position
	 */
	public abstract int getToken(int propIndex, int pos);

}
package nl.inl.blacklab.search.fimatch;

/** Source of tokens for the forward index matching process. */
public abstract class TokenSource {

	/** Return token at specified position.
	 *
	 * @param pos position to read
	 * @param propIndex property to read
	 * @return token at this position
	 */
	public abstract int getToken(int pos, int propIndex);

	/**
	 * Get the direction for this token source.
	 *
	 * @return -1 for right to left, 1 for left to right
	 */
	public abstract int getDirection();

}
package nl.inl.blacklab.search.fimatch;

/** Source of tokens for the forward index matching process. */
public abstract class TokenSource {

	protected int startingPosition;

	protected int direction;

	public TokenSource(int startingPosition, int direction) {
		this.startingPosition = startingPosition;
		this.direction = direction;
	}

	/** Return token at specified position.
	 *
	 * @param propIndex property to read
	 * @param pos position to read
	 * @return token at this position
	 */
	public abstract int getToken(int propIndex, int pos);

	/**
	 * Get the direction for this token source.
	 *
	 * @return -1 for right to left, 1 for left to right
	 */
	public int getDirection() {
		return direction;
	}

	/**
	 * Is this a valid position in the token source?
	 *
	 * @param pos position to check
	 * @return true if it is, false if it isn't
	 */
	public abstract boolean validPos(int pos);

}
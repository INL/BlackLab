package nl.inl.blacklab.search;

/**
 * One token and its frequency in some context.
 */
public class TokenFrequency implements Comparable<TokenFrequency> {

	/** What token this frequency is for */
	public String token;

	/** How many times the token occurs in the context */
	public long frequency;

	/**
	 * Construct a collocation
	 * @param token a token (word, lemma, pos, etc.)
	 * @param frequency the token's frequency in the context
	 */
	public TokenFrequency(String token, int frequency) {
		super();
		this.token = token;
		this.frequency = frequency;
	}

	@Override
	public String toString() {
		return token + " (" + frequency +")";
	}

	/**
	 * Natural ordering of TokenFrequency is by decreasing frequency.
	 */
	@Override
	public int compareTo(TokenFrequency o) {
		long delta = o.frequency - frequency;
		return delta == 0 ? 0 : (delta < 0 ? -1 : 1);
	}

	@Override
	public int hashCode() {
		return (int) (token.hashCode() + frequency);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj instanceof TokenFrequency) {
			return ((TokenFrequency) obj).token.equals(token) && ((TokenFrequency) obj).frequency == frequency;
		}
		return false;
	}



}
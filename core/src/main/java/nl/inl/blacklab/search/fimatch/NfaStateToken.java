package nl.inl.blacklab.search.fimatch;

import java.util.Collection;
import java.util.List;

/**
 * Represents both a state in an NFA, and a complete NFA
 * with this as the starting state.
 */
public class NfaStateToken extends NfaState {

	private static final int ANY_TOKEN = -1;

	/** What property we're trying to match */
	private int propertyNumber;

	/** The this state accepts. */
	private int inputToken;

	/** The next state if a matching token was found. */
	private NfaState nextState;

	public NfaStateToken(int propertyNumber, int inputToken, NfaState nextState) {
		this.propertyNumber = propertyNumber;
		this.inputToken = inputToken;
		this.nextState = nextState;
	}

	/**
	 * Find all matches for this NFA in the token source.
	 *
	 * @param tokenSource where to read tokens from
	 * @param pos current matching position
	 * @param matchEnds where to collect the matches found, or null if we don't want to collect them
	 * @return true if any (new) matches were found, false if not
	 */
	public boolean findMatches(TokenSource tokenSource, int pos, List<Integer> matchEnds) {
		// Token state. Check if it matches token from token source, and if so, continue.
		if (inputToken == ANY_TOKEN && tokenSource.validPos(pos) || tokenSource.getToken(propertyNumber, pos) == inputToken) {
			if (nextState == null)
				throw new RuntimeException("nextState == null in token state (" + propertyNumber + ", " + inputToken + ")");
			return nextState.findMatches(tokenSource, pos + 1, matchEnds);
		}
		return false;
	}

	@Override
	void fillDangling(NfaState state) {
		if (nextState == null)
			nextState = state;
	}

	@Override
	NfaStateToken copyInternal(Collection<NfaState> dangling) {
		NfaStateToken copy = new NfaStateToken(propertyNumber, inputToken, nextState);
		if (nextState == null)
			dangling.add(copy);
		return copy;
	}

	@Override
	public void setNextState(NfaState state) {
		nextState = state;
	}

}

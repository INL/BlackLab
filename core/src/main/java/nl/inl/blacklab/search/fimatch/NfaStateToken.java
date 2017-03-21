package nl.inl.blacklab.search.fimatch;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Represents both a state in an NFA, and a complete NFA
 * with this as the starting state.
 */
public class NfaStateToken extends NfaState {

	static final int ANY_TOKEN = Integer.MAX_VALUE;

	/** What property we're trying to match */
	private int propertyNumber;

	/** The this state accepts. */
	private int inputToken;

	/** The next state if a matching token was found. */
	protected NfaState nextState;

	/** (debug) The token string, so we can see it in debug output */
	private String dbgTokenString;

	public NfaStateToken(int propertyNumber, int inputToken, NfaState nextState, String dbgTokenString) {
		this.propertyNumber = propertyNumber;
		this.inputToken = inputToken;
		this.nextState = nextState;
		this.dbgTokenString = dbgTokenString;
	}

	/**
	 * Find all matches for this NFA in the token source.
	 *
	 * @param fiDoc where to read tokens from
	 * @param pos current matching position
	 * @param matchEnds where to collect the matches found, or null if we don't want to collect them
	 * @return true if any (new) matches were found, false if not
	 */
	@Override
	public boolean findMatchesInternal(ForwardIndexDocument fiDoc, int pos, int direction, Set<Integer> matchEnds) {
		// Token state. Check if it matches token from token source, and if so, continue.
		int actualToken = fiDoc.getToken(propertyNumber, pos);
		if (inputToken == ANY_TOKEN && actualToken >= 0 || actualToken == inputToken) {
			if (nextState == null) {
				// null stands for the match state
				if (matchEnds != null)
					matchEnds.add(pos + direction);
				return true;
			}
			return nextState.findMatchesInternal(fiDoc, pos + direction, direction, matchEnds);
		}
		return false;
	}

	@Override
	void fillDangling(NfaState state) {
		if (nextState == null)
			nextState = state;
	}

	@Override
	NfaStateToken copyInternal(Collection<NfaState> dangling, Map<NfaState, NfaState> copiesMade) {
		NfaStateToken copy = new NfaStateToken(propertyNumber, inputToken, nextState, dbgTokenString);
		if (nextState == null)
			dangling.add(copy);
		return copy;
	}

	@Override
	public void setNextState(int i, NfaState state) {
		if (i != 0)
			throw new RuntimeException("Token state only has one next state");
		nextState = state;
	}

	@Override
	public boolean matchesEmptySequence(Set<NfaState> statesVisited) {
		return false;
	}

	@Override
	public boolean hitsAllSameLength(Set<NfaState> statesVisited) {
		return true;
	}

	@Override
	public int hitsLengthMin(Set<NfaState> statesVisited) {
		return 1;
	}

	@Override
	public int hitsLengthMax(Set<NfaState> statesVisited) {
		return 1;
	}

	@Override
	protected String dumpInternal(Map<NfaState, Integer> stateNrs) {
		return "TOKEN(" + dbgTokenString + "," + dump(nextState, stateNrs) + ")";
	}

}

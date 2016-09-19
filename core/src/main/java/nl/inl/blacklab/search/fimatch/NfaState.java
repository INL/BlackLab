package nl.inl.blacklab.search.fimatch;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Represents both a state in an NFA, and a complete NFA
 * with this as the starting state.
 */
public class NfaState {

	/** A state that leads to two other states without any input tokens */
	private static final int TYPE_SPLIT_STATE = -1;

	/** Match (final) state */
	private static final int TYPE_MATCH_STATE = -2;

	/** Any token state */
	private static final int TYPE_ANY_TOKEN = -3;

	/** Singleton instance of the final state */
	private static final NfaState THE_MATCH_STATE = new NfaState(-1, TYPE_MATCH_STATE, null, null);

	/** What property we're trying to match in this state (if this is a token state) */
	private int propertyNumber;

	/**
	 * The type of state and the input token it accepts, if applicable.
	 *
	 * If -1, this is a split state.
	 * If -2, this is the match (final) state.
	 * If >= 0, token to accept to go to nextState.
	 */
	private int stateType;

	/** For a match state, the next state if a matching token was found.
	 *  For a split state, the first state to try. */
	private NfaState nextState;

	/** For a split state, the second state to try. */
	private NfaState nextState2;

	private NfaState(int propertyNumber, int inputToken, NfaState nextState, NfaState nextState2) {
		this.propertyNumber = propertyNumber;
		this.stateType = inputToken;
		this.nextState = nextState;
		this.nextState2 = nextState2;
	}

	/**
	 * Build a token state.
	 *
	 * @param propertyNumber what property to match
	 * @param inputToken what token to match
	 * @param nextState what state to go to after a succesful match
	 * @return the state object
	 */
	public static NfaState token(int propertyNumber, int inputToken, NfaState nextState) {
		return new NfaState(propertyNumber, inputToken, nextState, null);
	}

	public static NfaState anyToken(NfaState nextState) {
		return new NfaState(TYPE_ANY_TOKEN, -1, nextState, null);
	}

	/**
	 * Build a split state.
	 *
	 * @param nextState first state to try
	 * @param nextState2 second state to try
	 * @return the state object
	 */
	public static NfaState split(NfaState nextState, NfaState nextState2) {
		return new NfaState(-1, TYPE_SPLIT_STATE, nextState, nextState2);
	}

	/**
	 * Return the match (final) state.
	 * @return the match (final) state
	 */
	public static NfaState match() {
		return THE_MATCH_STATE;
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
		switch (stateType) {
		case TYPE_MATCH_STATE:
			// Match state. Record the match we found.
			if (matchEnds != null)
				matchEnds.add(pos);
			return true;
		case TYPE_SPLIT_STATE:
			// Split state. Find matches for both alternatives.
			if (nextState == null)
				throw new RuntimeException("nextState == null in split state");
			boolean a = nextState.findMatches(tokenSource, pos, matchEnds);
			if (nextState2 == null)
				throw new RuntimeException("nextState2 == null in split state");
			boolean b = nextState2.findMatches(tokenSource, pos, matchEnds);
			return a || b;
		case TYPE_ANY_TOKEN:
			if (tokenSource.validPos(pos))
				return nextState.findMatches(tokenSource, pos + 1, matchEnds);
		default:
			// Token state. Check if it matches token from token source, and if so, continue.
			if (tokenSource.getToken(propertyNumber, pos) == stateType) {
				if (nextState == null)
					throw new RuntimeException("nextState == null in token state (" + propertyNumber + ", " + stateType + ")");
				return nextState.findMatches(tokenSource, pos + 1, matchEnds);
			}
			return false;
		}
	}

	public boolean matches(TokenSource tokenSource, int pos) {
		return findMatches(tokenSource, pos, null);
	}

	void fillDangling(NfaState state) {
		if (stateType == TYPE_MATCH_STATE)
			return;
		if (nextState == null)
			nextState = state;
		if (nextState2 == null && stateType == TYPE_SPLIT_STATE)
			nextState2 = state;
	}

	NfaState copy(Collection<NfaState> dangling, Map<NfaState, NfaState> copiesMade) {
		NfaState existingCopy = copiesMade.get(this);
		if (existingCopy != null)
			return existingCopy;
		NfaState n = nextState == null ? null : nextState.copy(dangling, copiesMade);
		NfaState n2 = nextState2 == null ? null : nextState2.copy(dangling, copiesMade);
		NfaState copy = new NfaState(propertyNumber, stateType, n, n2);
		if (isTokenState() && nextState == null) {
			dangling.add(copy);
		}
		copiesMade.put(this, copy);
		return copy;
	}

	private boolean isTokenState() {
		return stateType >= 0;
	}

	public void setNextState(NfaState state) {
		nextState = state;
	}

	void setNextState2(NfaState state) {
		nextState2 = state;
	}
}

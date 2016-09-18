package nl.inl.blacklab.search.fimatch;

import java.util.Collection;
import java.util.List;

/**
 * Represents both a state in an NFA, and a complete NFA
 * with this as the starting state.
 */
public class NfaState {

	public static final int SPLIT_STATE = -1;

	public static final int FINAL_STATE = -2;

	/**
	 * What property we're trying to match in this state (if this is a token state)
	 */
	private int propertyNumber;

	/**
	 * If >= 0, token to accept to go to nextState. If -1, this is a split state.
	 * If -2, this is the match (final) state.
	 */
	private int inputToken;

	private NfaState nextState;

	private NfaState nextState2;

	NfaState(int propertyNumber, int inputToken, NfaState nextState, NfaState nextState2) {
		this.propertyNumber = propertyNumber;
		this.inputToken = inputToken;
		this.nextState = nextState;
		this.nextState2 = nextState2;
	}

	public static NfaState token(int propertyNumber, int inputToken, NfaState nextState) {
		return new NfaState(propertyNumber, inputToken, nextState, null);
	}

	public static NfaState split(NfaState nextState, NfaState nextState2) {
		return new NfaState(-1, SPLIT_STATE, nextState, nextState2);
	}

	public static NfaState match(int inputToken, NfaState nextState) {
		return new NfaState(-1, FINAL_STATE, null, null);
	}

	/**
	 * Find all hits for this NFA in the token source.
	 *
	 * @param tokenSource where to read tokens from
	 * @param pos current matching position
	 * @param matchEnds where to collect the hits found
	 */
	public void findHits(TokenSource tokenSource, int pos, List<Integer> matchEnds) {
		if (inputToken == FINAL_STATE) {
			// Match state. Record the match we found.
			matchEnds.add(pos);
			return;
		}
		if (inputToken == SPLIT_STATE) {
			// Split state. Find matches for both alternatives.
			nextState.findHits(tokenSource, pos, matchEnds);
			nextState2.findHits(tokenSource, pos, matchEnds);
		}
		// Token state. Check if it matches token from token source, and if so, continue.
		if (tokenSource.getToken(propertyNumber, pos) == inputToken) {
			nextState.findHits(tokenSource, pos++, matchEnds);
		}
	}

	public void fillDangling(NfaState state) {
		if (inputToken == FINAL_STATE)
			return;
		if (nextState == null)
			nextState = state;
		if (nextState2 == null && inputToken == SPLIT_STATE)
			nextState2 = state;
	}

	public NfaState copy(Collection<NfaState> dangling) {
		NfaState n = nextState == null ? null : nextState.copy(dangling);
		NfaState n2 = nextState2 == null ? null : nextState2.copy(dangling);
		NfaState copy = new NfaState(propertyNumber, inputToken, n, n2);
		if (isTokenState() && nextState == null) {
			dangling.add(copy);
		}
		return copy;
	}

	private boolean isTokenState() {
		return inputToken >= 0;
	}
}

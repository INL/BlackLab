package nl.inl.blacklab.search.fimatch;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents both a state in an NFA, and a complete NFA
 * with this as the starting state.
 */
public abstract class NfaState {

	/** Singleton instance of the final state */
	private static final NfaState THE_MATCH_STATE = new NfaStateMatch();

	/**
	 * Build a token state.
	 *
	 * @param propertyNumber what property to match
	 * @param inputToken what token to match
	 * @param nextState what state to go to after a succesful match
	 * @return the state object
	 */
	public static NfaState token(int propertyNumber, int inputToken, NfaState nextState) {
		return new NfaStateToken(propertyNumber, inputToken, nextState);
	}

	public static NfaState anyToken(NfaState nextState) {
		return new NfaStateAnyToken(nextState);
	}

	/**
	 * Build am OR state.
	 *
	 * @param nextStates states to try
	 * @return the state object
	 */
	public static NfaState or(NfaState... nextStates) {
		return new NfaStateOr(nextStates);
	}

	/**
	 * Build an OR state.
	 *
	 * @param nfaClauses NFAs, one of which must match
	 * @return the state object
	 */
	public static NfaState or(List<NfaState> nfaClauses) {
		return new NfaStateOr(nfaClauses);
	}

	/**
	 * Build an AND state.
	 *
	 * @param nfaClauses NFAs that must match
	 * @return the state object
	 */
	public static NfaState and(List<NfaState> nfaClauses) {
		return new NfaStateAnd(nfaClauses);
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
	abstract boolean findMatchesInternal(TokenSource tokenSource, int pos, Set<Integer> matchEnds);

	/**
	 * Find all matches for this NFA in the token source.
	 *
	 * @param tokenSource where to read tokens from
	 * @param pos current matching position
	 * @return the matches found, if any
	 */
	public Set<Integer> findMatches(TokenSource tokenSource, int pos) {
		Set<Integer> results = new HashSet<>();
		findMatchesInternal(tokenSource, pos, results);
		return results;
	}

	public boolean matches(TokenSource tokenSource, int pos) {
		return findMatchesInternal(tokenSource, pos, null);
	}

	abstract void fillDangling(NfaState state);

	final NfaState copy(Collection<NfaState> dangling, Map<NfaState, NfaState> copiesMade) {
		NfaState existingCopy = copiesMade.get(this);
		if (existingCopy != null)
			return existingCopy;
		NfaState copy = copyInternal(dangling, copiesMade);
		copiesMade.put(this, copy);
		return copy;
	}

	abstract NfaState copyInternal(Collection<NfaState> dangling, Map<NfaState, NfaState> copiesMade);

	public abstract void setNextState(int i, NfaState state);
}

package nl.inl.blacklab.search.fimatch;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

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
	 * @param direction matching direction
	 * @param matchEnds where to collect the matches found, or null if we don't want to collect them
	 * @return true if any (new) matches were found, false if not
	 */
	abstract boolean findMatchesInternal(TokenSource tokenSource, int pos, int direction, Set<Integer> matchEnds);

	/**
	 * Find all matches for this NFA in the token source.
	 *
	 * @param tokenSource where to read tokens from
	 * @param pos current matching position
	 * @param direction matching direction
	 * @return the matches found, if any
	 */
	public SortedSet<Integer> findMatches(TokenSource tokenSource, int pos, int direction) {
		SortedSet<Integer> results = new TreeSet<>();
		findMatchesInternal(tokenSource, pos, direction, results);
		return results;
	}

	/**
	 * Does the token source match this NFA?
	 *
	 * @param tokenSource where to read tokens from
	 * @param pos current matching position
	 * @param direction matching direction
	 * @return true if tokenSource matches, false if not
	 */
	public boolean matches(TokenSource tokenSource, int pos, int direction) {
		return findMatchesInternal(tokenSource, pos, direction, null);
	}

	/**
	 * For any dangling output states this state has, fill in the specified state.
	 *
	 * @param state state to fill in for dangling output states
	 */
	abstract void fillDangling(NfaState state);

	/**
	 * Return a copy of the fragment starting from this state, and collect all (copied) states with dangling outputs.
	 *
	 * @param dangling where to collect copied states with dangling outputs
	 * @param copiesMade states copied earlier during this copy operation, so we can deal with cyclic NFAs (i.e. don't keep copying,
	 *   re-use the previous copy)
	 * @return the copied fragment
	 */
	final NfaState copy(Collection<NfaState> dangling, Map<NfaState, NfaState> copiesMade) {
		NfaState existingCopy = copiesMade.get(this);
		if (existingCopy != null)
			return existingCopy;
		NfaState copy = copyInternal(dangling, copiesMade);
		copiesMade.put(this, copy);
		return copy;
	}

	/**
	 * Return a copy of the fragment starting from this state, and collect all (copied) states with dangling outputs.
	 *
	 * Subclasses can override this (not copy()), so they don't have to look at copiesMade but can always just create a
	 * copy of themselves.
	 *
	 * @param dangling where to collect copied states with dangling outputs
	 * @param copiesMade states copied earlier during this copy operation, so we can deal with cyclic NFAs (i.e. don't keep copying,
	 *   re-use the previous copy)
	 * @return the copied fragment
	 */
	abstract NfaState copyInternal(Collection<NfaState> dangling, Map<NfaState, NfaState> copiesMade);

	/**
	 * Set the next state for a given input.
	 *
	 * @param input input
	 * @param state next state
	 */
	public abstract void setNextState(int input, NfaState state);

	@Override
	public String toString() {
		return "NfaState";
	}

	/**
	 * Does this NFA match the empty sequence?
	 * @param statesVisited states we've already visited, so we can deal with cycles
	 * @return true if it matches the empty sequence, false if not
	 */
	public abstract boolean matchesEmptySequence(Set<NfaState> statesVisited);

	/**
	 * Are all hits from this NFA the same length?
	 * @param statesVisited states we've already visited, so we can deal with cycles
	 * @return true if all hits are the same length, false if not
	 */
	public abstract boolean hitsAllSameLength(Set<NfaState> statesVisited);

	/**
	 * What's the minimum hit length?
	 * @param statesVisited states we've already visited, so we can deal with cycles
	 * @return minimum hit length
	 */
	public abstract int hitsLengthMin(Set<NfaState> statesVisited);

	/**
	 * What's the maximum hit length?
	 * @param statesVisited states we've already visited, so we can deal with cycles
	 * @return maximum hit length
	 */
	public abstract int hitsLengthMax(Set<NfaState> statesVisited);

	public static Set<NfaState> emptySet() {
		return Collections.newSetFromMap(new IdentityHashMap<NfaState, Boolean>());
	}

}

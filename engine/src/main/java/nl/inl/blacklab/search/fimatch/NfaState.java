package nl.inl.blacklab.search.fimatch;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Represents both a state in an NFA, and a complete NFA with this as the
 * starting state.
 *
 * (Note that the "matching state" is simply represented as null in the NFAs we
 * build, which is convenient when possibly appending other NFAs to it later)
 */
public abstract class NfaState {

    /** Singleton instance of the match final state */
    private static final NfaState THE_MATCH_STATE = new NfaStateMatch();

    /**
     * Build a token state.
     *
     * @param luceneField what annotation to match
     * @param inputToken what token to match
     * @param nextState what state to go to after a succesful match
     * @return the state object
     */
    public static NfaState token(String luceneField, String inputToken, NfaState nextState) {
        return new NfaStateToken(luceneField, inputToken, nextState);
    }

    /**
     * Build a token state.
     *
     * @param luceneField what annotation to match
     * @param inputTokens what tokens to match
     * @param nextState what state to go to after a succesful match
     * @return the state object
     */
    public static NfaState token(String luceneField, Set<String> inputTokens, NfaState nextState) {
        return new NfaStateToken(luceneField, inputTokens, nextState);
    }

    public static NfaState regex(String luceneField, String pattern, NfaState nextState) {
        return new NfaStateRegex(luceneField, pattern, nextState);
    }

    public static NfaState anyToken(String luceneField, NfaState nextState) {
        return new NfaStateAnyToken(luceneField, nextState);
    }

    /**
     * Build am OR state.
     *
     * @param clausesMayLoopBack if false, no clauses loop back to earlier states,
     *            (a more efficient way of matching can be used in this case)
     * @param nextStates states to try
     * @param clausesAllSameLength are all hits for all clauses the same length?
     *            (used to optimize matching)
     * @return the state object
     */
    public static NfaState or(boolean clausesMayLoopBack, List<NfaState> nextStates, boolean clausesAllSameLength) {
        return clausesMayLoopBack ? new NfaStateOr(nextStates, clausesAllSameLength)
                : new NfaStateOrAcyclic(nextStates, clausesAllSameLength);
    }

    /**
     * Build an AND state.
     *
     * @param clausesMayLoopBack if false, no clauses loop back to earlier states,
     *            (a more efficient way of matching can be used in this case)
     * @param nextStates NFAs that must match
     * @return the state object
     */
    public static NfaState and(boolean clausesMayLoopBack, List<NfaState> nextStates) {
        return clausesMayLoopBack ? new NfaStateAnd(nextStates) : new NfaStateAndAcyclic(nextStates);
    }

    public static NfaState match() {
        return THE_MATCH_STATE;
    }

    /**
     * Find all matches for this NFA in the token source.
     *
     * @param fiDoc where to read tokens from
     * @param pos current matching position
     * @param direction matching direction
     * @param matchEnds where to collect the matches found, or null if we don't want
     *            to collect them
     * @return true if any (new) matches were found, false if not
     */
    abstract boolean findMatchesInternal(ForwardIndexDocument fiDoc, int pos, int direction, Set<Integer> matchEnds);

    /**
     * Find all matches for this NFA in the token source.
     *
     * @param fiDoc where to read tokens from
     * @param pos current matching position
     * @param direction matching direction
     * @return the matches found, if any
     */
    public NavigableSet<Integer> findMatches(ForwardIndexDocument fiDoc, int pos, int direction) {
        NavigableSet<Integer> results = new TreeSet<>();
        findMatchesInternal(fiDoc, pos, direction, results);
        return results;
    }

    /**
     * Does the token source match this NFA?
     *
     * @param fiDoc where to read tokens from
     * @param pos current matching position
     * @param direction matching direction
     * @return true if fiDoc matches, false if not
     */
    public boolean matches(ForwardIndexDocument fiDoc, int pos, int direction) {
        return findMatchesInternal(fiDoc, pos, direction, null);
    }

    /**
     * For any dangling output states this state has, fill in the specified state.
     *
     * @param state state to fill in for dangling output states
     */
    abstract void fillDangling(NfaState state);

    /**
     * Return a copy of the fragment starting from this state, and collect all
     * (copied) states with dangling outputs.
     *
     * @param dangling where to collect copied states with dangling outputs, or null
     *            if we don't care about these
     * @param copiesMade states copied earlier during this copy operation, so we can
     *            deal with cyclic NFAs (i.e. don't keep copying, re-use the
     *            previous copy)
     * @return the copied fragment
     */
    final NfaState copy(Collection<NfaState> dangling, Map<NfaState, NfaState> copiesMade) {
        NfaState existingCopy = copiesMade.get(this);
        if (existingCopy != null)
            return existingCopy;
        return copyInternal(dangling, copiesMade);
    }

    /**
     * Return a copy of the fragment starting from this state, and collect all
     * (copied) states with dangling outputs.
     *
     * Subclasses can override this (not copy()), so they don't have to look at
     * copiesMade but can always just create a copy of themselves.
     *
     * @param dangling where to collect copied states with dangling outputs, or null
     *            if we don't care about these
     * @param copiesMade states copied earlier during this copy operation, so we can
     *            deal with cyclic NFAs (i.e. don't keep copying, re-use the
     *            previous copy)
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
        Map<NfaState, Integer> stateNrs = new IdentityHashMap<>();
        return "NFA:" + dump(stateNrs);
    }

    /**
     * Visit each node and replace dangling arrows (nulls) with the match state.
     *
     * @param visited nodes visited so far, so we don't visit nodes multiple times
     */
    public void finish(Set<NfaState> visited) {
        if (visited.contains(this))
            return;
        visited.add(this);
        finishInternal(visited);
    }

    /**
     * Visit each node and replace dangling arrows (nulls) with the match state.
     *
     * @param visited nodes visited so far, this one included. finish() uses this to
     *            make sure we don't visit the same node twice, so always call
     *            finish() in your implementations, not finishInternal().
     */
    protected abstract void finishInternal(Set<NfaState> visited);

    public static String dump(NfaState state, Map<NfaState, Integer> stateNrs) {
        return state == null ? "DANGLING" : state.dump(stateNrs);
    }

    public String dump(Map<NfaState, Integer> stateNrs) {
        Integer n = stateNrs.get(this);
        if (n != null)
            return "#" + n;
        n = stateNrs.size() + 1;
        stateNrs.put(this, n);
        return "#" + n + ":" + dumpInternal(stateNrs);
    }

    protected abstract String dumpInternal(Map<NfaState, Integer> stateNrs);

    /**
     * Does this NFA match the empty sequence?
     * 
     * @param statesVisited states we've already visited, so we can deal with cycles
     * @return true if it matches the empty sequence, false if not
     */
    public abstract boolean matchesEmptySequence(Set<NfaState> statesVisited);

    /**
     * Are all hits from this NFA the same length?
     * 
     * @param statesVisited states we've already visited, so we can deal with cycles
     * @return true if all hits are the same length, false if not
     */
    public abstract boolean hitsAllSameLength(Set<NfaState> statesVisited);

    /**
     * What's the minimum hit length?
     * 
     * @param statesVisited states we've already visited, so we can deal with cycles
     * @return minimum hit length
     */
    public abstract int hitsLengthMin(Set<NfaState> statesVisited);

    /**
     * What's the maximum hit length?
     * 
     * @param statesVisited states we've already visited, so we can deal with cycles
     * @return maximum hit length
     */
    public abstract int hitsLengthMax(Set<NfaState> statesVisited);

    public static Set<NfaState> emptySet() {
        return Collections.newSetFromMap(new IdentityHashMap<NfaState, Boolean>());
    }

    public final void lookupPropertyNumbers(ForwardIndexAccessor fiAccessor, Map<NfaState, Boolean> statesVisited) {
        if (statesVisited.containsKey(this))
            return;
        statesVisited.put(this, true);
        lookupPropertyNumbersInternal(fiAccessor, statesVisited);
    }

    abstract void lookupPropertyNumbersInternal(ForwardIndexAccessor fiAccessor, Map<NfaState, Boolean> statesVisited);

}

package nl.inl.blacklab.search.fimatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An OR-node where none of the clauses cycle back to an
 * earlier node. This can be evaluated more efficiently,
 * by finding matches for each of the clauses, and then
 * continuing at nextState from each of the matches found.
 */
public class NfaStateOrAcyclic extends NfaState {

	List<NfaState> orClauses;

	NfaState nextState;

	public NfaStateOrAcyclic(NfaState... orClauses) {
		this.orClauses = new ArrayList<>(Arrays.asList(orClauses));
		this.nextState = null;
	}

	@Override
	public boolean findMatchesInternal(ForwardIndexDocument fiDoc, int pos, int direction, Set<Integer> matchEnds) {
		// OR/Split state. Find matches for all alternatives.
		boolean orClauseMatched = false;
		Set<Integer> orMatchEnds = new HashSet<>();
		for (NfaState orClause: orClauses) {
			boolean matchesFound = false;
			if (orClause == null) {
				// null stands for the matching state.
				orMatchEnds.add(pos);
				matchesFound = true;
			} else {
				matchesFound = orClause.findMatchesInternal(fiDoc, pos, direction, orMatchEnds);
			}
			if (matchesFound && matchEnds == null && nextState == null)
				return true; // we don't care about the match ends, just that there are matches
			orClauseMatched |= matchesFound;
		}
		boolean foundMatch = false;
		if (orClauseMatched) {
			if (nextState == null) {
				// null corresponds to the match state, so our or-clause matches are our final matches
				matchEnds.addAll(orMatchEnds);
				return true;
			}
			// Continue matching from the matches to our OR clauses
			for (Integer orMatchEnd: orMatchEnds) {
				foundMatch |= nextState.findMatchesInternal(fiDoc, orMatchEnd, direction, matchEnds);
			}
		}
		return foundMatch;
	}

	@Override
	void fillDangling(NfaState state) {
		if (nextState == null)
			nextState = state;
	}

	@Override
	NfaStateOrAcyclic copyInternal(Collection<NfaState> dangling, Map<NfaState, NfaState> copiesMade) {
		NfaStateOrAcyclic copy = new NfaStateOrAcyclic();
		copiesMade.put(this, copy);
		List<NfaState> clauseCopies = new ArrayList<>();
		for (NfaState orClause: orClauses) {
			if (orClause != null)
				orClause = orClause.copy(null, copiesMade);
			clauseCopies.add(orClause);
		}
		copy.orClauses.addAll(clauseCopies);
		copy.nextState = nextState;
		if (nextState == null && dangling != null)
			dangling.add(copy);
		return copy;
	}

	@Override
	public void setNextState(int input, NfaState state) {
		orClauses.set(input, state);
	}

	@Override
	public boolean matchesEmptySequence(Set<NfaState> statesVisited) {
		if (statesVisited.contains(this)) {
			// We've found a cycle. Stop processing, and just return the
			// "safest" (least-guarantee) answer. In this case: we can't
			// guarantee that this DOESN'T match the empty sequence.
			return true;
		}
		statesVisited.add(this);
		boolean anyMatchEmpty = false;
		for (NfaState orClause: orClauses) {
			if (orClause.matchesEmptySequence(statesVisited)) {
				anyMatchEmpty = true;
				break;
			}
		}
		if (anyMatchEmpty && (nextState == null || nextState.matchesEmptySequence(statesVisited))) {
			return true;
		}
		return false;
	}

	@Override
	public boolean hitsAllSameLength(Set<NfaState> statesVisited) {
		if (statesVisited.contains(this)) {
			// We've found a cycle. Stop processing, and just return the
			// "safest" (least-guarantee) answer. In this case: we can't
			// guarantee that hits are all the same length.
			return false;
		}
		statesVisited.add(this);
		int hitLength = -1;
		for (NfaState orClause: orClauses) {
			if (!orClause.hitsAllSameLength(statesVisited))
				return false;
			if (hitLength != -1 && hitLength != orClause.hitsLengthMin(statesVisited))
				return false;
			hitLength = orClause.hitsLengthMin(statesVisited);
		}
		return nextState == null || nextState.hitsAllSameLength(statesVisited);
	}

	@Override
	public int hitsLengthMin(Set<NfaState> statesVisited) {
		int hitLengthMin = Integer.MAX_VALUE;
		if (statesVisited.contains(this)) {
			// We've found a cycle. Stop processing, and just return the
			// "safest" (least-guarantee) answer. In this case: the smallest
			// hit might be 0 long.
			return 0;
		}
		statesVisited.add(this);
		for (NfaState nextState: orClauses) {
			int i = nextState.hitsLengthMin(statesVisited);
			if (i < hitLengthMin)
				hitLengthMin = i;
		}
		if (hitLengthMin < Integer.MAX_VALUE && nextState != null) {
			hitLengthMin += nextState.hitsLengthMin(statesVisited);
		}
		return hitLengthMin;
	}

	@Override
	public int hitsLengthMax(Set<NfaState> statesVisited) {
		int hitLengthMax = -1;
		if (statesVisited.contains(this)) {
			// We've found a cycle. Stop processing, and just return the
			// "safest" (least-guarantee) answer. In this case: the largest
			// hit might be "infinitely" large.
			return Integer.MAX_VALUE;
		}
		statesVisited.add(this);
		for (NfaState nextState: orClauses) {
			int i = nextState.hitsLengthMax(statesVisited);
			if (i > hitLengthMax)
				hitLengthMax = i;
		}
		if (hitLengthMax >= 0 && nextState != null) {
			hitLengthMax += nextState.hitsLengthMax(statesVisited);
		}
		if (hitLengthMax < 0)
			return 0;
		return hitLengthMax;
	}

	@Override
	protected String dumpInternal(Map<NfaState, Integer> stateNrs) {
		StringBuilder b = new StringBuilder();
		for (NfaState s: orClauses) {
			if (b.length() > 0)
				b.append(",");
			b.append(dump(s, stateNrs));
		}
		return "OR(" + b.toString() + ", " + dump(nextState, stateNrs) + ")";
	}

	@Override
	void lookupPropertyNumbersInternal(ForwardIndexAccessor fiAccessor, Map<NfaState, Boolean> statesVisited) {
		for (NfaState s: orClauses) {
			if (s != null)
				s.lookupPropertyNumbers(fiAccessor, statesVisited);
		}
		if (nextState != null)
			nextState.lookupPropertyNumbers(fiAccessor, statesVisited);
	}

}

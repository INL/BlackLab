package nl.inl.blacklab.search.fimatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NfaStateAnd extends NfaState {

	List<NfaState> nextStates;

	public NfaStateAnd(NfaState... nextStates) {
		this.nextStates = new ArrayList<>(Arrays.asList(nextStates));
	}

	public NfaStateAnd(List<NfaState> nextStates) {
		this.nextStates = new ArrayList<>(nextStates);
	}

	@Override
	public boolean findMatchesInternal(TokenSource tokenSource, int pos, int direction, Set<Integer> matchEnds) {
		// Split state. Find matches for all alternatives.
		int i = 0;
		Set<Integer> newHitsFound = null;
		for (NfaState nextState: nextStates) {
			if (nextState == null)
				throw new RuntimeException("nextState[" + i + "] == null in AND state");
			i++;
			Set<Integer> matchesForClause = nextState.findMatches(tokenSource, pos, direction);
			if (newHitsFound == null) {
				newHitsFound = matchesForClause;
			} else {
				// Calculate intersection
				newHitsFound.retainAll(matchesForClause);
			}
			if (newHitsFound.size() == 0)
				break; // no hits
		}
		if (matchEnds != null)
			matchEnds.addAll(newHitsFound);
		return newHitsFound.size() > 0;
	}

	@Override
	void fillDangling(NfaState state) {
		for (int i = 0; i < nextStates.size(); i++) {
			if (nextStates.get(i) == null)
				nextStates.set(i, state);
		}
	}

	@Override
	NfaStateAnd copyInternal(Collection<NfaState> dangling, Map<NfaState, NfaState> copiesMade) {
		List<NfaState> clauseCopies = new ArrayList<>();
		boolean hasNulls = false;
		for (NfaState nextState: nextStates) {
			if (nextState == null)
				hasNulls = true;
			else
				nextState = nextState.copy(dangling, copiesMade);
			clauseCopies.add(nextState);
		}
		NfaStateAnd copy = new NfaStateAnd(clauseCopies);
		if (hasNulls)
			dangling.add(copy);
		return copy;
	}

	@Override
	public void setNextState(int i, NfaState state) {
		this.nextStates.set(i, state);
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
		for (NfaState nextState: nextStates) {
			if (!nextState.matchesEmptySequence(statesVisited))
				return false;
		}
		return true;
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
		for (NfaState nextState: nextStates) {
			// If any of the following states has all-same-length hits,
			// this state must also have them.
			if (nextState.hitsAllSameLength(statesVisited))
				return true;
		}
		return true;
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
		for (NfaState nextState: nextStates) {
			int i = nextState.hitsLengthMin(statesVisited);
			if (i < hitLengthMin)
				hitLengthMin = i;
		}
		return hitLengthMin;
	}

	@Override
	public int hitsLengthMax(Set<NfaState> statesVisited) {
		int hitLengthMax = 0;
		if (statesVisited.contains(this)) {
			// We've found a cycle. Stop processing, and just return the
			// "safest" (least-guarantee) answer. In this case: the largest
			// hit might be "infinitely" large.
			return Integer.MAX_VALUE;
		}
		statesVisited.add(this);
		for (NfaState nextState: nextStates) {
			int i = nextState.hitsLengthMax(statesVisited);
			if (i > hitLengthMax)
				hitLengthMax = i;
		}
		return hitLengthMax;
	}

}

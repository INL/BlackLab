package nl.inl.blacklab.search.fimatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NfaStateOr extends NfaState {

	List<NfaState> nextStates;

	public NfaStateOr(NfaState... nextStates) {
		this.nextStates = new ArrayList<>(Arrays.asList(nextStates));
	}

	public NfaStateOr(List<NfaState> nextStates) {
		this.nextStates = new ArrayList<>(nextStates);
	}

	@Override
	public boolean findMatchesInternal(TokenSource tokenSource, int pos, int direction, Set<Integer> matchEnds) {
		// OR/Split state. Find matches for all alternatives.
		int i = 0;
		boolean result = false;
		for (NfaState nextState: nextStates) {
			if (nextState == null)
				throw new RuntimeException("nextState[" + i + "] == null in OR state");
			i++;
			boolean a = nextState.findMatchesInternal(tokenSource, pos, direction, matchEnds);
			if (a && matchEnds == null)
				return true; // we don't care about the match ends, just that there are matches
			result |= a;
		}
		return result;
	}

	@Override
	void fillDangling(NfaState state) {
		for (int i = 0; i < nextStates.size(); i++) {
			if (nextStates.get(i) == null)
				nextStates.set(i, state);
		}
	}

	@Override
	NfaStateOr copyInternal(Collection<NfaState> dangling, Map<NfaState, NfaState> copiesMade) {
		List<NfaState> clauseCopies = new ArrayList<>();
		boolean hasNulls = false;
		for (NfaState nextState: nextStates) {
			if (nextState == null)
				hasNulls = true;
			else
				nextState = nextState.copy(dangling, copiesMade);
			clauseCopies.add(nextState);
		}
		NfaStateOr copy = new NfaStateOr(clauseCopies);
		if (hasNulls)
			dangling.add(copy);
		return copy;
	}

	@Override
	public void setNextState(int input, NfaState state) {
		this.nextStates.set(input, state);
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
			if (nextState.matchesEmptySequence(statesVisited))
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
		for (NfaState nextState: nextStates) {
			if (!nextState.hitsAllSameLength(statesVisited))
				return false;
			if (hitLength != -1 && hitLength != nextState.hitsLengthMin(statesVisited))
				return false;
			hitLength = nextState.hitsLengthMin(statesVisited);
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

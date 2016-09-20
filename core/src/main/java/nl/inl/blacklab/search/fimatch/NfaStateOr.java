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
	public boolean findMatchesInternal(TokenSource tokenSource, int pos, Set<Integer> matchEnds) {
		// OR/Split state. Find matches for all alternatives.
		int i = 0;
		boolean result = false;
		for (NfaState nextState: nextStates) {
			if (nextState == null)
				throw new RuntimeException("nextState[" + i + "] == null in OR state");
			i++;
			boolean a = nextState.findMatchesInternal(tokenSource, pos, matchEnds);
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
	public void setNextState(int i, NfaState state) {
		this.nextStates.set(i, state);
	}

}

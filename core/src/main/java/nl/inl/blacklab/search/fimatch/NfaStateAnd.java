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
	public boolean findMatchesInternal(TokenSource tokenSource, int pos, Set<Integer> matchEnds) {
		// Split state. Find matches for all alternatives.
		int i = 0;
		Set<Integer> newHitsFound = null;
		for (NfaState nextState: nextStates) {
			if (nextState == null)
				throw new RuntimeException("nextState[" + i + "] == null in AND state");
			i++;
			Set<Integer> matchesForClause = nextState.findMatches(tokenSource, pos);
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

}

package nl.inl.blacklab.search.fimatch;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class NfaStateMatch extends NfaState {

	@Override
	public boolean findMatchesInternal(TokenSource tokenSource, int pos, int direction, Set<Integer> matchEnds) {
		if (matchEnds != null)
			matchEnds.add(pos);
		return true;
	}

	@Override
	void fillDangling(NfaState state) {
		// nothing to do
	}

	@Override
	NfaState copyInternal(Collection<NfaState> dangling, Map<NfaState, NfaState> copiesMade) {
		return this; // immutable, singleton
	}

	@Override
	public void setNextState(int i, NfaState state) {
		throw new UnsupportedOperationException("Match state has no next state");
	}

	@Override
	public boolean matchesEmptySequence(Set<NfaState> statesVisited) {
		return true;
	}

	@Override
	public boolean hitsAllSameLength(Set<NfaState> statesVisited) {
		return true;
	}

	@Override
	public int hitsLengthMin(Set<NfaState> statesVisited) {
		return 0;
	}

	@Override
	public int hitsLengthMax(Set<NfaState> statesVisited) {
		return 0;
	}

}

package nl.inl.blacklab.search.fimatch;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class NfaStateNoMatch extends NfaState {

	@Override
	public boolean findMatchesInternal(ForwardIndexDocument fiDoc, int pos, int direction, Set<Integer> matchEnds) {
		return false;
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
		throw new UnsupportedOperationException("'No match' state has no next state");
	}

	@Override
	public boolean matchesEmptySequence(Set<NfaState> statesVisited) {
		return false;
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

	@Override
	protected String dumpInternal(Map<NfaState, Integer> stateNrs) {
		return "NOMATCH()";
	}

}

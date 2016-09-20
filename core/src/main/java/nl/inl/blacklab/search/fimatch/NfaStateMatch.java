package nl.inl.blacklab.search.fimatch;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class NfaStateMatch extends NfaState {

	@Override
	public boolean findMatchesInternal(TokenSource tokenSource, int pos, Set<Integer> matchEnds) {
		if (matchEnds != null)
			matchEnds.add(pos + 1);
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

}

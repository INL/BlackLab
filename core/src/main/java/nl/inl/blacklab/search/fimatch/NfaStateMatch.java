package nl.inl.blacklab.search.fimatch;

import java.util.Collection;
import java.util.List;

public class NfaStateMatch extends NfaState {

	@Override
	public boolean findMatches(TokenSource tokenSource, int pos,
			List<Integer> matchEnds) {
		if (matchEnds != null)
			matchEnds.add(pos);
		return true;
	}

	@Override
	void fillDangling(NfaState state) {
		// nothing to do
	}

	@Override
	NfaState copyInternal(Collection<NfaState> dangling) {
		return this; // immutable, singleton
	}

	@Override
	public void setNextState(NfaState state) {
		throw new UnsupportedOperationException("Match state has no next state");
	}

}

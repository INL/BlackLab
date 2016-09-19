package nl.inl.blacklab.search.fimatch;

import java.util.Collection;
import java.util.List;

public class NfaStateSplit extends NfaState {

	NfaState nextState, nextState2;

	public NfaStateSplit(NfaState nextState, NfaState nextState2) {
		this.nextState = nextState;
		this.nextState2 = nextState2;
	}

	@Override
	public boolean findMatches(TokenSource tokenSource, int pos,
			List<Integer> matchEnds) {
		// Split state. Find matches for both alternatives.
		if (nextState == null)
			throw new RuntimeException("nextState == null in split state");
		boolean a = nextState.findMatches(tokenSource, pos, matchEnds);
		if (a && matchEnds == null)
			return true; // we don't care about the match ends, just that there are matches
		if (nextState2 == null)
			throw new RuntimeException("nextState2 == null in split state");
		boolean b = nextState2.findMatches(tokenSource, pos, matchEnds);
		return a || b;
	}

	@Override
	void fillDangling(NfaState state) {
		if (nextState == null)
			nextState = state;
		if (nextState2 == null)
			nextState2 = state;
	}

	@Override
	NfaStateSplit copyInternal(Collection<NfaState> dangling) {
		NfaStateSplit copy = new NfaStateSplit(nextState, nextState2);
		if (nextState == null || nextState2 == null)
			dangling.add(copy);
		return copy;
	}

	@Override
	public void setNextState(NfaState state) {
		this.nextState = state;
	}

	@Override
	public void setNextState2(NfaState state) {
		this.nextState2 = state;
	}

}

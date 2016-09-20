package nl.inl.blacklab.search.fimatch;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class NfaStateNot extends NfaState {

	private NfaState clause;

	public NfaStateNot(NfaState clause) {
		this.clause = clause;
		if (clause == null)
			throw new IllegalArgumentException("NOT clause cannot be null");
	}

	@Override
	boolean findMatchesInternal(TokenSource tokenSource, int pos, Set<Integer> matchEnds) {
		boolean noMatches = !clause.findMatchesInternal(tokenSource, pos, null);
		if (noMatches) {
			// No matches found at this position, therefore this token is a NOT match.
			if (matchEnds != null)
				matchEnds.add(pos + 1);
		}
		return noMatches;
	}

	@Override
	void fillDangling(NfaState state) {
		// nothing to do
	}

	@Override
	NfaState copyInternal(Collection<NfaState> dangling, Map<NfaState, NfaState> copiesMade) {
		return new NfaStateNot(clause.copy(dangling, copiesMade));
	}

	@Override
	public void setNextState(int i, NfaState state) {
		throw new UnsupportedOperationException("Cannot change NOT clause");
	}

}

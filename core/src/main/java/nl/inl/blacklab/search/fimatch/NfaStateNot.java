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
	boolean findMatchesInternal(TokenSource tokenSource, int pos, int direction, Set<Integer> matchEnds) {
		boolean clauseMatches = clause.findMatchesInternal(tokenSource, pos, direction, null);
		if (clauseMatches)
			return false;
		// No matches found at this position, therefore this token IS a match.
		if (matchEnds != null)
			matchEnds.add(pos + direction);
		return true;
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
		return 1;
	}

	@Override
	public int hitsLengthMax(Set<NfaState> statesVisited) {
		return 1;
	}

}

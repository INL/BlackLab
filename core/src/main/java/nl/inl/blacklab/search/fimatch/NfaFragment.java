package nl.inl.blacklab.search.fimatch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A fragment of an NFA being built.
 * Contains a starting state and a list of dangling end states
 */
public class NfaFragment {

	NfaState startingState;

	Collection<NfaState> danglingArrows;

	public NfaFragment(NfaState startingState, Collection<NfaState> danglingArrows) {
		this.startingState = startingState;
		this.danglingArrows = danglingArrows;
	}

	public NfaState getStartingState() {
		return startingState;
	}

	private Collection<NfaState> getDanglingArrows() {
		return danglingArrows;
	}

	public void sequence(NfaFragment state) {
		for (NfaState d: danglingArrows) {
			d.fillDangling(state.getStartingState());
		}
		danglingArrows.clear();
		danglingArrows.addAll(state.getDanglingArrows());
	}

	public NfaFragment copy() {
		List<NfaState> dangling = new ArrayList<>();
		NfaState stateCopy = startingState.copy(dangling);
		return new NfaFragment(stateCopy, dangling);
	}

	public void repeat(int min, int max) {
		if (min == 0) {
			// Optional clause. Introduce split state at start.
			NfaState start = NfaState.split(startingState, null);
			if (max < 0) {
				// Infinite. Loop back to start state.
				for (NfaState d: danglingArrows) {
					d.fillDangling(start);
				}
				danglingArrows.clear();
			} else {
				// Finite. Add the right number of copies.
				NfaFragment copy = this;
				for (int i = 1; i < max; i++) {
					copy = copy();
					sequence(copy());
				}
			}
			danglingArrows.add(start);
			startingState = start;
		} else {
			// Non-optional. First add the right number of copies
			for (int i = 1; i < min; i++) {

			}
			if (max < 0) {
				// Infinite.
			} else {
				// Finite. Add the right number of copies.
				NfaFragment copy = this;
				for (int i = 1; i < max; i++) {
					copy = copy();
					sequence(copy());
				}
			}
		}
	}
}

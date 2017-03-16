package nl.inl.blacklab.search.fimatch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
		this.danglingArrows = new ArrayList<>();
		if (danglingArrows != null)
			this.danglingArrows.addAll(danglingArrows);
	}

	public NfaState getStartingState() {
		return startingState;
	}

	private void setStartingState(NfaState start) {
		this.startingState = start;
	}

	public NfaFragment copy() {
		List<NfaState> dangling = new ArrayList<>();
		Map<NfaState, NfaState> copiesMade = new IdentityHashMap<>();
		NfaState copy = startingState.copy(dangling, copiesMade);
		return new NfaFragment(copy, dangling);
	}

	public void append(NfaFragment state) {
		for (NfaState d: danglingArrows) {
			d.fillDangling(state.getStartingState());
		}
		danglingArrows.clear();
		danglingArrows.addAll(state.getDanglingArrows());
	}

	public void repeat(int min, int max) {
		// {min, max} with min > 0 or * (0 or more)
		// First, create a sequence of min copies of the clause.
		// Then, follow up with (max - min) links with an escape arrow
		// each (or loop back to the last copy of the min sequence for
		// max == infinite)

		// Create the min part
		NfaFragment link = this; // we'll loop back to link later if max == infinite
		if (min > 1) {
			for (int i = 1; i < min; i++) {
				link = link.copy();
				append(link);
			}
		}

		// Create the max part, depending on whether it's infinite or not.
		if (max < 0) {
			// Infinite. Loop back to start of last link added.
			NfaState loopBack = NfaState.or(link.getStartingState(), null);
			for (NfaState d: danglingArrows) {
				d.fillDangling(loopBack);
			}
			danglingArrows.clear();
			danglingArrows.add(loopBack);
			if (min == 0) {
				// For * (0 or more), loopback becomes the starting state, so we
				// don't have to go through the clause but can skip it entirely.
				startingState = loopBack;
			}
		} else {
			// Finite. Allow (max - min) more occurrences of the clause.
			if (min != 0) {
				// There's already required occurrences of the clause.
				// Make a copy of link first so we don't destroy those.
				link = link.copy();
			}
			// Make optional clause fragment (note that if min == 0, this link
			// will already be in its correct place)
			NfaState start = NfaState.or(link.getStartingState(), null);
			Set<NfaState> escapeArrows = new HashSet<>();
			escapeArrows.add(start);
			link.setStartingState(start);

			// How many optional clauses do we still need to add?
			int numberOfLinksToAdd = max - min;
			if (min == 0) {
				// We already have one link (see above), so add one less.
				numberOfLinksToAdd--;
				if (numberOfLinksToAdd > 0) {
					// The first link is the one we already made.
					// This copy is the next link we'll add.
					link = link.copy();
				}
			}
			for (int i = 0; i < numberOfLinksToAdd; i++) {
				append(link);
				escapeArrows.add(link.getStartingState());
				if (i < numberOfLinksToAdd - 1)
					link = link.copy(); // this will be the next link
			}
			// Escape arrows may have been filled in by append(). Set them to null again so
			// they will be treated as dangling arrows.
			for (NfaState state: escapeArrows) {
				state.setNextState(1, null);
				danglingArrows.add(state);
			}
		}
	}

	public void invert() {
		NfaState not = new NfaStateNot(startingState);
		startingState = not;
	}

	public Collection<NfaState> getDanglingArrows() {
		return danglingArrows;
	}

	public NfaState finish() {
		append(new NfaFragment(NfaState.match(), null)); // finish NFA
		return startingState;
	}

	@Override
	public String toString() {
		return "NfaFragment";
	}

}

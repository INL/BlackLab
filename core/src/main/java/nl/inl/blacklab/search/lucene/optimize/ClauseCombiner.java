package nl.inl.blacklab.search.lucene.optimize;

import java.util.HashSet;
import java.util.Set;

import nl.inl.blacklab.search.lucene.BLSpanQuery;

public abstract class ClauseCombiner {

	private static Set<ClauseCombiner> all;

	public abstract int priority(BLSpanQuery left, BLSpanQuery right);

	public abstract BLSpanQuery combine(BLSpanQuery left, BLSpanQuery right);

	public boolean canCombine(BLSpanQuery left, BLSpanQuery right) {
		return priority(left, right) != Integer.MAX_VALUE;
	}

	public static Set<ClauseCombiner> getAll() {
		if (all == null) {
			all = new HashSet<>();
			all.add(new RepetitionCombiner());
		}
		return all;
	}
}
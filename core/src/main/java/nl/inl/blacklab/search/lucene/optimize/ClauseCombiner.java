package nl.inl.blacklab.search.lucene.optimize;

import java.util.HashSet;
import java.util.Set;

import nl.inl.blacklab.search.lucene.BLSpanQuery;

public abstract class ClauseCombiner {

	private static Set<ClauseCombiner> all;
	
	public static final int CANNOT_COMBINE = Integer.MAX_VALUE;

	public abstract int priority(BLSpanQuery left, BLSpanQuery right);

	public abstract BLSpanQuery combine(BLSpanQuery left, BLSpanQuery right);

	public boolean canCombine(BLSpanQuery left, BLSpanQuery right) {
		return priority(left, right) != CANNOT_COMBINE;
	}

	public static Set<ClauseCombiner> getAll() {
		if (all == null) {
			all = new HashSet<>();
			all.add(new ClauseCombinerRepetition());
			all.add(new ClauseCombinerInternalisation());
			all.add(new ClauseCombinerAnyExpansion());
			all.add(new ClauseCombinerNot());
		}
		return all;
	}
}
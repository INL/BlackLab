package nl.inl.blacklab.search.lucene.optimize;

import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.NfaFragment;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryFiSeq;

/**
 * Converts one clause to an NFA and uses the other as the anchor in a
 * FISEQ operation to match using the forward index.
 */
public class ClauseCombinerNfa extends ClauseCombiner {

	private static final int PRIORITY = 1000;

	/**
	 * The default value of nfaFactor.
	 */
	public static final int DEFAULT_NFA_FACTOR = 100;

	/**
	 * The maximum value of nfaFactor, meaning "make as many NFAs as possible".
	 */
	public static final int MAX_NFA_FACTOR = Integer.MAX_VALUE;

	/**
	 * The maximum value of nfaFactor, meaning "make as many NFAs as possible".
	 */
	public static final int NO_NFA_FACTOR = -1;

	/**
	 * The ratio of estimated numbers of hits that we use to decide
	 * whether or not to try NFA-matching with two clauses / subsequences.
	 */
	private static int nfaFactor = DEFAULT_NFA_FACTOR;

	public static void setNfaFactor(int nfaFactor) {
		ClauseCombinerNfa.nfaFactor = nfaFactor;
	}

	public static void setNfaMatchingEnabled(boolean b) {
		nfaFactor = b ? DEFAULT_NFA_FACTOR : NO_NFA_FACTOR;
	}
	enum Type {
		ANCHOR_NFA,
		NFA_ANCHOR
	}

	Type getType(BLSpanQuery left, BLSpanQuery right, IndexReader reader) {
		if (nfaFactor > 0 && right.canMakeNfa()) {
			// Yes, see if it's worth it to combine with it.
			long numPrev = Math.max(1, left.estimatedNumberOfHits(reader));
			long numThis = right.estimatedNumberOfHits(reader);
			if (nfaFactor == Integer.MAX_VALUE || numThis / numPrev > nfaFactor) {
				return Type.ANCHOR_NFA;
			}
		}
		return null;
	}

	@Override
	public int priority(BLSpanQuery left, BLSpanQuery right, IndexReader reader) {
		return getType(left, right, reader) == null ? CANNOT_COMBINE : PRIORITY;
	}

	@Override
	public BLSpanQuery combine(BLSpanQuery left, BLSpanQuery right, IndexReader reader) {
		// Could we make an NFA out of this clause?
		switch(getType(left, right, reader)) {
		case ANCHOR_NFA:
			// Yes, worth it.
			ForwardIndexAccessor fiAccessor = ForwardIndexAccessor.fromSearcher(Searcher.fromIndexReader(reader), right.getField());
			NfaFragment nfaFrag = right.getNfa(fiAccessor, 1);
			if (left instanceof SpanQueryFiSeq && ((SpanQueryFiSeq)left).getDirection() == 1) {
				// Existing FISEQ; add NFA to it.
				return ((SpanQueryFiSeq)left).appendNfa(nfaFrag);
			}
			// New FISEQ.
			return new SpanQueryFiSeq(left, false, nfaFrag, 1, fiAccessor);
		case NFA_ANCHOR:
			throw new UnsupportedOperationException();
		}
		throw new UnsupportedOperationException("Cannot combine " + left + " and " + right);
	}

}

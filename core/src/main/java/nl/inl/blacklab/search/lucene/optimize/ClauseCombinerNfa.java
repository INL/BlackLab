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

	private static final int FORWARD_PRIORITY = 10000000;

	private static final int BACKWARD_PRIORITY = 20000000;

	/**
	 * The default value of nfaFactor.
	 */
	public static final int DEFAULT_NFA_FACTOR = 100;

	/**
	 * The maximum value of nfaFactor, meaning "make as many NFAs as possible".
	 */
	public static final int MAX_NFA_FACTOR = 0;

	/**
	 * The minimum value of nfaFactor, meaning "make no NFAs".
	 */
	public static final int NO_NFA_FACTOR = Integer.MAX_VALUE;

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

	private static int getFactor(BLSpanQuery left, BLSpanQuery right, IndexReader reader) {
		if (nfaFactor == NO_NFA_FACTOR)
			return 0;
		boolean leftEmpty = left.matchesEmptySequence();
		boolean rightEmpty = right.matchesEmptySequence();
		long numLeft = Math.max(1, left.estimatedNumberOfHits(reader));
		long numRight = Math.max(1, right.estimatedNumberOfHits(reader));
		boolean leftNfa = left.canMakeNfa();
		boolean rightNfa = right.canMakeNfa();
		boolean backwardPossible = leftNfa && !rightEmpty;
		boolean forwardPossible = rightNfa && !leftEmpty;
		if (forwardPossible || backwardPossible) {
			// Possible.
			if (forwardPossible && backwardPossible) {
				// Both possible; choose the best
				if (numRight >= numLeft) {
					// Forward
					return (int)(numRight / numLeft) + 1;
				}
				// Backward
				return -(int)(numLeft / numRight) - 1;
			}
			// One possibility; which one?
			if (forwardPossible)
				return (int)(numRight / numLeft) + 1;
			return -(int)(numLeft / numRight) - 1;
		}
		return 0; // not possible
	}

	@Override
	public int priority(BLSpanQuery left, BLSpanQuery right, IndexReader reader) {
		if (nfaFactor == NO_NFA_FACTOR)
			return CANNOT_COMBINE;
		int factor = getFactor(left, right, reader);
		if (factor == 0)
			return CANNOT_COMBINE;
		int absFactor = Math.abs(factor);
		if (absFactor < nfaFactor)
			return CANNOT_COMBINE;
		return factor > 0 ? FORWARD_PRIORITY + absFactor : BACKWARD_PRIORITY + absFactor;
	}

	@Override
	public BLSpanQuery combine(BLSpanQuery left, BLSpanQuery right, IndexReader reader) {
		// Could we make an NFA out of this clause?
		int factor = getFactor(left, right, reader);
		if (factor == 0)
			throw new UnsupportedOperationException("Cannot combine " + left + " and " + right);
		if (factor > 0) {
			// Forward
			if (left instanceof SpanQueryFiSeq && ((SpanQueryFiSeq)left).getDirection() == 1) {
				// Existing forward FISEQ; add NFA to it (re-use fiAccessor so properties get same index).
				ForwardIndexAccessor fiAccessor = ((SpanQueryFiSeq)left).getFiAccessor();
				NfaFragment nfaFrag = right.getNfa(fiAccessor, 1);
				return ((SpanQueryFiSeq)left).appendNfa(nfaFrag);
			}
			// New FISEQ.
			ForwardIndexAccessor fiAccessor = ForwardIndexAccessor.fromSearcher(Searcher.fromIndexReader(reader), right.getField());
			NfaFragment nfaFrag = right.getNfa(fiAccessor, 1);
			return new SpanQueryFiSeq(left, false, nfaFrag, 1, fiAccessor);
		}

		// Backward
		if (right instanceof SpanQueryFiSeq && ((SpanQueryFiSeq)right).getDirection() == -1) {
			// Existing backward FISEQ; add NFA to it (re-use fiAccessor so properties get same index).
			ForwardIndexAccessor fiAccessor = ((SpanQueryFiSeq)right).getFiAccessor();
			NfaFragment nfaFrag = left.getNfa(fiAccessor, -1);
			return ((SpanQueryFiSeq)right).appendNfa(nfaFrag);
		}
		// New FISEQ.
		ForwardIndexAccessor fiAccessor = ForwardIndexAccessor.fromSearcher(Searcher.fromIndexReader(reader), left.getField());
		NfaFragment nfaFrag = left.getNfa(fiAccessor, -1);
		return new SpanQueryFiSeq(right, true, nfaFrag, -1, fiAccessor);

	}

}

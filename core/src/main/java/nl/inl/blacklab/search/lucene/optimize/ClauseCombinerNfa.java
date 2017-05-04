package nl.inl.blacklab.search.lucene.optimize;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.NfaTwoWay;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryFiSeq;

/**
 * Converts one clause to an NFA and uses the other as the anchor in a
 * FISEQ operation to match using the forward index.
 */
public class ClauseCombinerNfa extends ClauseCombiner {

	protected static final Logger logger = LogManager.getLogger(ClauseCombinerNfa.class);

	private static final int FORWARD_PRIORITY = 10000000;

	private static final int BACKWARD_PRIORITY = 20000000;

	/**
	 * The default value of nfaThreshold.
	 */
	public static final long DEFAULT_NFA_THRESHOLD = 900;

	/**
	 * The maximum value of nfaFactor, meaning "make as many NFAs as possible".
	 */
	public static final long MAX_NFA_MATCHING = Long.MAX_VALUE;

	/**
	 * The minimum value of nfaFactor, meaning "make no NFAs".
	 */
	public static final long NO_NFA_MATCHING = 0;

	/**
	 * Indicates how expensive fetching a lot of term positions from Lucene is;
	 * Used to calculate the cost of "regular" matching.
	 */
	private static final long TERM_FREQ_PENALTY_FACTOR = 500;

	/**
	 * What we multiply our calculated cost ratio by to get an integer in a reasonable
	 * range.
	 */
	private static final long COST_RATIO_CONSTANT_FACTOR = 1000;

	/**
	 * The ratio of estimated numbers of hits that we use to decide
	 * whether or not to try NFA-matching with two clauses / subsequences.
	 * The lower the number, the more we use NFA-matching.
	 */
	private static long nfaThreshold = DEFAULT_NFA_THRESHOLD;

	public static void setNfaThreshold(long nfaThreshold) {
		ClauseCombinerNfa.nfaThreshold = nfaThreshold;
	}

	public static void setNfaMatchingEnabled(boolean doNfaMatching) {
		boolean doingNfaMatching = nfaThreshold != NO_NFA_MATCHING;
		if (doNfaMatching != doingNfaMatching)
			nfaThreshold = doNfaMatching ? DEFAULT_NFA_THRESHOLD : NO_NFA_MATCHING;
	}

	private static long getFactor(BLSpanQuery left, BLSpanQuery right, IndexReader reader) {
		if (nfaThreshold == NO_NFA_MATCHING)
			return 0;
		boolean leftEmpty = left.matchesEmptySequence();
		boolean rightEmpty = right.matchesEmptySequence();
		long numLeft = Math.max(1, left.reverseMatchingCost(reader));
		long numRight = Math.max(1, right.reverseMatchingCost(reader));
		long seqReverseCost = Math.min(numLeft, numRight) + (numLeft + numRight) / TERM_FREQ_PENALTY_FACTOR;
		int fiCostLeft = left.forwardMatchingCost();
		int fiCostRight = right.forwardMatchingCost();
		long costNfaToReverseForward = COST_RATIO_CONSTANT_FACTOR * numLeft * fiCostRight / seqReverseCost;
		long costNfaToReverseBackward = COST_RATIO_CONSTANT_FACTOR * numRight * fiCostLeft / seqReverseCost;
		boolean leftNfa = left.canMakeNfa();
		boolean rightNfa = right.canMakeNfa();
		boolean backwardPossible = leftNfa && !rightEmpty;
		boolean forwardPossible = rightNfa && !leftEmpty;
		//fp1 bp1 rf242624 rb2568 fil5 fir1 nl27114064 nr57411
		//factor == -2569, abs(factor) > nfaThreshold (2000)
		logger.debug(String.format("   fp%d bp%d rf%d rb%d fil%d fir%d nl%d nr%d",
			forwardPossible ? 1 : 0,
			backwardPossible ? 1 : 0,
			costNfaToReverseForward,
			costNfaToReverseBackward,
			fiCostLeft,
			fiCostRight,
			numLeft,
			numRight
		));
		if (forwardPossible || backwardPossible) {
			// Possible.
			if (forwardPossible && backwardPossible) {
				// Both possible; choose the best
				if (costNfaToReverseBackward >= costNfaToReverseForward) {
					// Forward
					return costNfaToReverseForward + 1;
				}
				// Backward
				return -costNfaToReverseBackward - 1;
			}
			// One possibility; which one?
			if (forwardPossible)
				return costNfaToReverseForward + 1;
			return -costNfaToReverseBackward - 1;
		}
		return 0; // not possible
	}

	@Override
	public int priority(BLSpanQuery left, BLSpanQuery right, IndexReader reader) {
		if (nfaThreshold == NO_NFA_MATCHING) {
			logger.debug("   nfa matching switched off");
			return CANNOT_COMBINE;
		}
		long factor = getFactor(left, right, reader);
		if (factor == 0) {
			logger.debug("   factor == 0");
			return CANNOT_COMBINE;
		}
		long absFactor = Math.abs(factor);
		if (absFactor > nfaThreshold) {
			logger.debug("   factor == " + factor + ", abs(factor) > nfaThreshold ("+nfaThreshold+")");
			return CANNOT_COMBINE;
		}
		return factor > 0 ? FORWARD_PRIORITY - (int)(10000 / absFactor) : BACKWARD_PRIORITY - (int)(10000 / absFactor);
	}

	@Override
	public BLSpanQuery combine(BLSpanQuery left, BLSpanQuery right, IndexReader reader) {
		// Could we make an NFA out of this clause?
		long factor = getFactor(left, right, reader);
		if (factor == 0)
			throw new UnsupportedOperationException("Cannot combine " + left + " and " + right);
		if (factor > 0) {
			// Forward (i.e. left is anchor, right is NFA)
			if (left instanceof SpanQueryFiSeq && ((SpanQueryFiSeq)left).getDirection() == 1) {
				// Existing forward FISEQ; add NFA to it (re-use fiAccessor so properties get same index).
				return ((SpanQueryFiSeq)left).appendNfa(right);
			}
			// New FISEQ.
			ForwardIndexAccessor fiAccessor = ForwardIndexAccessor.fromSearcher(Searcher.fromIndexReader(reader), right.getField());
			NfaTwoWay nfaTwoWay = right.getNfaTwoWay(fiAccessor, 1);
			return new SpanQueryFiSeq(left, false, nfaTwoWay, right, 1, fiAccessor);
		}

		// Backward (i.e. right is anchor, left is NFA)
		if (right instanceof SpanQueryFiSeq && ((SpanQueryFiSeq)right).getDirection() == -1) {
			// Existing backward FISEQ; add NFA to it (re-use fiAccessor so properties get same index).
			return ((SpanQueryFiSeq)right).appendNfa(left);
		}
		// New FISEQ.
		ForwardIndexAccessor fiAccessor = ForwardIndexAccessor.fromSearcher(Searcher.fromIndexReader(reader), left.getField());
		NfaTwoWay nfaTwoWay = left.getNfaTwoWay(fiAccessor, -1);
		return new SpanQueryFiSeq(right, true, nfaTwoWay, left, -1, fiAccessor);

	}

}

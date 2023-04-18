package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Arrays;

import org.apache.lucene.search.spans.FilterSpans;

import nl.inl.blacklab.search.fimatch.ForwardIndexAccessorLeafReader;
import nl.inl.blacklab.search.fimatch.ForwardIndexDocument;
import nl.inl.blacklab.search.matchfilter.MatchFilter;

public class SpansConstrained extends BLFilterSpans<BLSpans> {

    /** The constraint with which we're filtering the clause */
    private final MatchFilter constraint;

    /** The hit query context, which contains captured group information */
    private HitQueryContext context;

    /** The current match info (captured groups, relations, etc.) */
    private MatchInfo[] matchInfo;

    /** Maps from term strings to term indices for each annotation. */
    private final ForwardIndexAccessorLeafReader fiAccessor;

    /** Where to get forward index tokens for the current doc */
    private ForwardIndexDocument currentFiDoc;

    public SpansConstrained(BLSpans in, MatchFilter constraint, ForwardIndexAccessorLeafReader fiAccessor) {
        super(in);
        this.constraint = constraint;
        this.fiAccessor = fiAccessor;
    }

    @Override
    public int nextDoc() throws IOException {
        int docId = super.nextDoc();
        return updateCurrentFiDoc(docId);
    }

    @Override
    public int advance(int target) throws IOException {
        int docId = super.advance(target);
        return updateCurrentFiDoc(docId);
    }

    /**
     * Returns true if the current document matches.
     * <p>This is called during two-phase processing.
     */
    // return true if the current document matches
    @SuppressWarnings("fallthrough")
    protected boolean twoPhaseCurrentDocMatches() throws IOException {
        updateCurrentFiDoc(in.docID());
        return super.twoPhaseCurrentDocMatches();
    }

    private int updateCurrentFiDoc(int docId) {
        if (docId == NO_MORE_DOCS)
            currentFiDoc = null;
        else if (currentFiDoc == null || docId != currentFiDoc.getSegmentDocId())
            currentFiDoc = fiAccessor.advanceForwardIndexDoc(docId);
        return docId;
    }

    @Override
    protected void passHitQueryContextToClauses(HitQueryContext context) {
        super.passHitQueryContextToClauses(context);
        this.context = context;
        constraint.setHitQueryContext(context);
    }

    @Override
    protected FilterSpans.AcceptStatus accept(BLSpans candidate) throws IOException {
        if (matchInfo == null) {
            matchInfo = new MatchInfo[context.getMatchInfoNames().size()];
        } else {
            Arrays.fill(matchInfo, null);
        }
        context.getMatchInfo(matchInfo);

        // OPT: if there are duplicate hits (including matchInfo), we'll
        //   evaluate the same constraint multiple times. Could be prevented
        //   by caching the previous results, but might not be worth it.
        if (constraint.evaluate(currentFiDoc, matchInfo).isTruthy())
            return FilterSpans.AcceptStatus.YES;
        return FilterSpans.AcceptStatus.NO;
    }

    @Override
    public String toString() {
        return "SpansConstrained(" + in + ", " + constraint + ")";
    }
}

package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Arrays;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.spans.SpanCollector;
import org.apache.lucene.search.spans.Spans;

import nl.inl.blacklab.search.fimatch.ForwardIndexAccessorLeafReader;
import nl.inl.blacklab.search.fimatch.ForwardIndexDocument;
import nl.inl.blacklab.search.matchfilter.MatchFilter;

public class SpansConstrained extends BLSpans {

    /** The clause we're filtering */
    private final BLSpans clause;

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

    /**
     * Are we already at the first match in a new document, before
     * nextStartPosition() has been called? Necessary because we have to make sure
     * nextDoc()/advance() actually puts us in a document with at least one match.
     */
    private boolean alreadyAtFirstMatch = false;

    public SpansConstrained(BLSpans clause, MatchFilter constraint, ForwardIndexAccessorLeafReader fiAccessor) {
        this.clause = clause;
        this.constraint = constraint;
        this.fiAccessor = fiAccessor;
    }

    @Override
    protected void passHitQueryContextToClauses(HitQueryContext context) {
        clause.setHitQueryContext(context);
    }

    @Override
    public void setHitQueryContext(HitQueryContext context) {
        super.setHitQueryContext(context);
        this.context = context;
        constraint.setHitQueryContext(context);
    }

    @Override
    public void getMatchInfo(MatchInfo[] relationInfo) {
        clause.getMatchInfo(relationInfo);
    }

    @Override
    public int nextDoc() throws IOException {
        alreadyAtFirstMatch = false;

        int currentDocId = clause.nextDoc();
        if (currentDocId == DocIdSetIterator.NO_MORE_DOCS) {
            currentFiDoc = null;
            return NO_MORE_DOCS;
        }
        return ensureValidDoc();
    }

    @Override
    public int advance(int target) throws IOException {
        alreadyAtFirstMatch = false;

        int currentDocId = clause.advance(target);
        if (currentDocId == DocIdSetIterator.NO_MORE_DOCS) {
            currentFiDoc = null;
            return NO_MORE_DOCS;
        }
        return ensureValidDoc();
    }

    /**
     * Ensure that the current document has at least one valid match; if not,
     * advance until we find a document with at least one valid match.
     *
     * @return the current doc id, or NO_MORE_DOCS if there are no more valid docs
     */
    private int ensureValidDoc() throws IOException {
        int currentDocId = clause.docID();
        while (currentDocId != NO_MORE_DOCS) {
            currentFiDoc = fiAccessor.advanceForwardIndexDoc(currentDocId);
            clause.nextStartPosition();
            if (ensureValidHit() != NO_MORE_POSITIONS) {
                alreadyAtFirstMatch = true;
                break;
            }
            currentDocId = clause.nextDoc();
        }
        return currentDocId;
    }

    @Override
    public int nextStartPosition() throws IOException {
        if (alreadyAtFirstMatch) {
            // We're already at the first match in the doc. Return it.
            alreadyAtFirstMatch = false;
            return clause.startPosition();
        }

        clause.nextStartPosition();
        return ensureValidHit();
    }

    @Override
    public int advanceStartPosition(int target) throws IOException {
        if (alreadyAtFirstMatch) {
            alreadyAtFirstMatch = false;
            if (clause.startPosition() >= target)
                return clause.startPosition();
        }

        clause.advanceStartPosition(target);
        return ensureValidHit();
    }

    /**
     * Ensure that the current hit is valid with respect to our constraint; if not,
     * advance to the next valid hit.
     *
     * @return current hit start position if valid, NO_MORE_POSITIONS if we're done
     *         in this document
     */
    protected int ensureValidHit() throws IOException {
        int startPos = clause.startPosition();
        while (startPos != Spans.NO_MORE_POSITIONS) {
            if (matchInfo == null) {
                matchInfo = new MatchInfo[context.getCapturedGroupNames().size()];
            } else {
                Arrays.fill(matchInfo, null);
            }
            context.getMatchInfo(matchInfo);
            if (constraint.evaluate(currentFiDoc, matchInfo).isTruthy())
                break;
            startPos = clause.nextStartPosition();
        }
        return startPos;
    }

    @Override
    public int startPosition() {
        if (alreadyAtFirstMatch)
            return -1; // nextStartPosition() hasn't been called yet
        return clause.startPosition();
    }

    @Override
    public int endPosition() {
        if (alreadyAtFirstMatch)
            return -1; // nextStartPosition() hasn't been called yet
        return clause.endPosition();
    }

    @Override
    public int width() {
        return clause.width();
    }

    @Override
    public void collect(SpanCollector collector) throws IOException {
        clause.collect(collector);
    }

    @Override
    public float positionsCost() {
        return clause.positionsCost();
    }

    @Override
    public int docID() {
        return clause.docID();
    }

}

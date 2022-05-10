package nl.inl.blacklab.search.lucene;

import java.util.Arrays;
import java.util.List;

/**
 * Combines SpanQueries using AND. Note that this means that only matches with
 * the same document id, the same start and the same end positions in all
 * SpanQueries will be kept.
 */
public class SpanQueryAnd extends SpanQueryAndNot {
    public SpanQueryAnd(BLSpanQuery first, BLSpanQuery second) {
        super(Arrays.asList(first, second), null);
    }

    public SpanQueryAnd(List<BLSpanQuery> clauscol) {
        super(clauscol, null);
    }

    public SpanQueryAnd(BLSpanQuery[] clauses) {
        super(Arrays.asList(clauses), null);
    }

    // no hashCode() and equals() because super class version is sufficient

}

package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Map;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanWeight;

/**
 * Base class for our SpanWeight classes. Ensures that getSpans returns a
 * BLSpans.
 */
public abstract class BLSpanWeight extends SpanWeight {

    public BLSpanWeight(SpanQuery query, IndexSearcher searcher, Map<Term, TermContext> termContexts)
            throws IOException {
        super(query, searcher, termContexts);
    }

    @Override
    public abstract BLSpans getSpans(LeafReaderContext ctx, Postings requiredPostings) throws IOException;

}

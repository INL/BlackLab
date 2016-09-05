package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.Spans;

/**
 * Adapt a SpanWeight to have getSpans() return a BLSpans if it didn't already.
 * Used for BLSpanTermQuery, among others.
 */
public class BLSpanWeightWrapper extends SpanWeight {

	private SpanWeight weight;

	public BLSpanWeightWrapper(SpanWeight weight, IndexSearcher searcher, Map<Term, TermContext> termContexts) throws IOException {
		super((SpanQuery)weight.getQuery(), searcher, termContexts);
		this.weight = weight;
	}

	@Override
	public void extractTermContexts(Map<Term, TermContext> contexts) {
		weight.extractTermContexts(contexts);
	}

	@Override
	public Spans getSpans(LeafReaderContext ctx, Postings requiredPostings) throws IOException {
		return BLSpansWrapper.optWrap(weight.getSpans(ctx, requiredPostings));
	}

	@Override
	public void extractTerms(Set<Term> terms) {
		weight.extractTerms(terms);
	}

}

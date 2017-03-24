package nl.inl.blacklab.search;

import nl.inl.blacklab.search.lucene.BLSpanQuery;

/**
 * Object used to explain how a SpanQuery is rewritten.
 */
public class QueryExplanation {
	
	private BLSpanQuery spanQuery;
	
	private BLSpanQuery rewrittenQuery;
	
	public QueryExplanation(BLSpanQuery spanQuery, BLSpanQuery rewrittenQuery) {
		super();
		this.spanQuery = spanQuery;
		this.rewrittenQuery = rewrittenQuery;
	}

	public BLSpanQuery getOriginalQuery() {
		return spanQuery;
	}

	public BLSpanQuery getRewrittenQuery() {
		return rewrittenQuery;
	}
}

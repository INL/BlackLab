package nl.inl.blacklab.search;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.Filter;
import org.apache.lucene.util.Bits;

/**
 * A Filter that only matches a single Lucene document id.
 *
 * Used for finding hits in a single document (for highlighting).
 */
public class SingleDocIdFilter extends Filter {

	private int luceneDocId;

	public SingleDocIdFilter(int luceneDocId) {
		this.luceneDocId = luceneDocId;
	}

	@Override
	public DocIdSet getDocIdSet(AtomicReaderContext ctx, Bits bits) {
		// Check that id could be in this segment, and bits allows this doc id
		if (luceneDocId >= ctx.docBase && (bits == null || bits.get(luceneDocId - ctx.docBase))) {
			// ctx is a single segment, so use docBase to adjust the id
			return new SingleDocIdSet(luceneDocId - ctx.docBase);
		}
		return DocIdSet.EMPTY;
	}

}

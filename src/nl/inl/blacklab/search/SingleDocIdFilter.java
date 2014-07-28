package nl.inl.blacklab.search;

import java.io.IOException;

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

	private DocIdSet docIdSet;

	public SingleDocIdFilter(int luceneDocId) {
		docIdSet = new SingleDocIdSet(luceneDocId);
	}

	@Override
	public DocIdSet getDocIdSet(AtomicReaderContext arg0, Bits arg1) throws IOException {
		// FIXME: shouldn't docIdSet be relative to the current reader context (arg0.docBase)?
		return docIdSet;
	}

}
/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Collection;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.spans.Spans;

/**
 * Apply a Filter to a Spans.
 *
 * This allows us to only consider certain documents (say, only documents in a certain domain) when
 * executing our query.
 */
public class SpansFiltered extends Spans {
	Spans spans;

	DocIdSetIterator docIdSetIter;

	boolean more;

	public SpansFiltered(Spans spans, Filter filter, IndexReader reader) throws IOException {
		this(spans, filter.getDocIdSet(reader));
	}

	public SpansFiltered(Spans spans, DocIdSet filterDocs) throws IOException {
		this.spans = spans;
		docIdSetIter = filterDocs.iterator();
		more = false;
		if (docIdSetIter != null) {
			more = (docIdSetIter.nextDoc() != DocIdSetIterator.NO_MORE_DOCS);
		}
	}

	private boolean synchronize() throws IOException {
		while (more && spans.doc() != docIdSetIter.docID()) {
			if (spans.doc() < docIdSetIter.docID())
				more = spans.skipTo(docIdSetIter.docID());
			else
				more = (docIdSetIter.advance(spans.doc()) != DocIdSetIterator.NO_MORE_DOCS);
		}
		return more;
	}

	@Override
	public boolean next() throws IOException {
		if (!more)
			return false;
		more = spans.next();
		return synchronize();
	}

	@Override
	public boolean skipTo(int target) throws IOException {
		if (!more)
			return false;
		more = spans.skipTo(target);
		return synchronize();
	}

	@Override
	public int doc() {
		return spans.doc();
	}

	@Override
	public int end() {
		return spans.end();
	}

	@Override
	public Collection<byte[]> getPayload() throws IOException {
		return spans.getPayload();
	}

	@Override
	public boolean isPayloadAvailable() {
		return spans.isPayloadAvailable();
	}

	@Override
	public int start() {
		return spans.start();
	}

}

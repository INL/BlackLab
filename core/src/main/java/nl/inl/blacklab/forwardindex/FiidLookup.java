package nl.inl.blacklab.forwardindex;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SlowCompositeReaderWrapper;
import org.apache.lucene.uninverting.UninvertingReader;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;

/**
 * Class for looking up forward index id, using DocValues or stored fields.
 */
class FiidLookup {

	/** Index reader, for getting documents (for translating from Lucene doc id to fiid) */
	private IndexReader reader;

	/** Cached fiid field */
	private NumericDocValues cachedFiids;

	/** fiid field name in the Lucene index (for translating from Lucene doc id to fiid) */
	private String fiidFieldName;

	public FiidLookup(IndexReader reader, String lucenePropFieldName) {
		this.fiidFieldName = ComplexFieldUtil.forwardIndexIdField(lucenePropFieldName);
		this.reader = reader;
		Map<String, UninvertingReader.Type> fields = new HashMap<>();
		fields.put(fiidFieldName, UninvertingReader.Type.INTEGER);

		try {
			// for (LeafReaderContext rc: reader.leaves()) {
			// LeafReader r = rc.reader();
			// uninv = new UninvertingReader(r, fields);
			// NumericDocValues leafCache = uninv.getNumericDocValues(fiidFieldName);
			//
			// }

			LeafReader srw = SlowCompositeReaderWrapper.wrap(reader);
			try (UninvertingReader uninv = new UninvertingReader(srw, fields)) {
				cachedFiids = uninv.getNumericDocValues(fiidFieldName);
			}
			int numToCheck = Math.min(ForwardIndexImplV3.NUMBER_OF_CACHE_ENTRIES_TO_CHECK, srw.maxDoc());
			if (!hasFiids(numToCheck))
				cachedFiids = null;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public long get(int docId) {
		if (cachedFiids != null)
			return cachedFiids.get(docId);

		// Not cached; find fiid by reading stored value from Document now
		try {
			return Integer.parseInt(reader.document(docId).get(fiidFieldName));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	public boolean hasFiids(int numToCheck) {
		// Check if the cache was retrieved OK
		boolean allZeroes = true;
		for (int i = 0; i < numToCheck; i++) {
			// (NOTE: we don't check if document wasn't deleted, but that shouldn't matter here)
			if (cachedFiids.get(i) != 0) {
				allZeroes = false;
				break;
			}
		}
		return !allZeroes;
	}
}
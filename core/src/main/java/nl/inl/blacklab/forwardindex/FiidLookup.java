package nl.inl.blacklab.forwardindex;

import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.uninverting.UninvertingReader;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;

/**
 * Class for looking up forward index id, using DocValues or stored fields.
 */
class FiidLookup {

	/** Index reader, for getting documents (for translating from Lucene doc id to fiid) */
	private IndexReader reader;

	/** fiid field name in the Lucene index (for translating from Lucene doc id to fiid) */
	private String fiidFieldName;

	/** The DocValues per segment (keyed by docBase) */
	private Map<Integer, NumericDocValues> cachedFiids;

	public FiidLookup(IndexReader reader, String lucenePropFieldName) {
		this.fiidFieldName = ComplexFieldUtil.forwardIndexIdField(lucenePropFieldName);
		this.reader = reader;
		Map<String, UninvertingReader.Type> fields = new TreeMap<>();
		fields.put(fiidFieldName, UninvertingReader.Type.INTEGER);
		cachedFiids = new TreeMap<>();
		try {
			for (LeafReaderContext rc: reader.leaves()) {
				LeafReader r = rc.reader();
				@SuppressWarnings("resource")
				UninvertingReader uninv = new UninvertingReader(r, fields);
				cachedFiids.put(rc.docBase, uninv.getNumericDocValues(fiidFieldName));
			}

			int numToCheck = Math.min(ForwardIndexImplV3.NUMBER_OF_CACHE_ENTRIES_TO_CHECK, reader.maxDoc());
			if (!hasFiids(numToCheck))
				cachedFiids = null;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public long get(int docId) {
		if (cachedFiids != null) {
			// Find the fiid in the correct segment
			Entry<Integer, NumericDocValues> prev = null;
			for (Entry<Integer, NumericDocValues> e: cachedFiids.entrySet()) {
				Integer docBase = e.getKey();
				if (docBase > docId) {
					// Previous segment (the highest docBase lower than docId) is the right one
					Integer prevDocBase = prev.getKey();
					NumericDocValues prevDocValues = prev.getValue();
					return prevDocValues.get(docId - prevDocBase);
				}
				prev = e;
			}
			// Last segment is the right one
			Integer prevDocBase = prev.getKey();
			NumericDocValues prevDocValues = prev.getValue();
			return prevDocValues.get(docId - prevDocBase);
		}

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
			if (get(i) != 0) {
				allZeroes = false;
				break;
			}
		}
		return !allZeroes;
	}
}
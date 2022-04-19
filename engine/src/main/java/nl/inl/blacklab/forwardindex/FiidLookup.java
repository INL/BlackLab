package nl.inl.blacklab.forwardindex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.solr.uninverting.UninvertingReader;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * Class for looking up forward index id, using DocValues or stored fields.
 *
 * This class is not thread-safe (using DocValues without synchronization).
 *
 * CAUTION: the advance() method can only be called with ascending doc ids!
 */
public class FiidLookup {

    /**
     * Index reader, for getting documents (for translating from Lucene doc id to
     * fiid)
     */
    private final IndexReader reader;

    /**
     * fiid field name in the Lucene index (for translating from Lucene doc id to
     * fiid)
     */
    private final String fiidFieldName;

    /** The DocValues per segment (keyed by docBase) */
    private Map<Integer, NumericDocValues> cachedFiids;

    public FiidLookup(IndexReader reader, Annotation annotation) {
        this.fiidFieldName = annotation.forwardIndexIdField();
        this.reader = reader;
        cachedFiids = new TreeMap<>();
        try {
            for (LeafReaderContext rc : reader.leaves()) {
                LeafReader r = rc.reader();
                NumericDocValues numericDocValues = r.getNumericDocValues(fiidFieldName);
                if (numericDocValues == null) {
                    // Use UninvertingReader to simulate DocValues (slower)
                    Map<String, UninvertingReader.Type> fields = new TreeMap<>();
                    fields.put(fiidFieldName, UninvertingReader.Type.INTEGER_POINT);
                    LeafReader uninv = UninvertingReader.wrap(r, fields::get);
                    numericDocValues = uninv.getNumericDocValues(fiidFieldName);
                }
                if (numericDocValues != null) {
                    cachedFiids.put(rc.docBase, numericDocValues);
                }
            }
            if (cachedFiids.isEmpty()) {
                // We don't actually have DocValues.
                cachedFiids = null;
            }
        } catch (IOException e) {
            BlackLabRuntimeException.wrap(e);
        }
    }

    /**
     * Return the forward index id for the given Lucene doc id.
     *
     * Uses DocValues to retrieve the fiid from the Lucene Document.
     *
     * CAUTION: docId must always be equal to or greater than the previous docId
     * this method was called with! (because DocValues API is sequential now)
     *
     * @param docId Lucene doc id
     * @return forward index id (fiid)
     */
    public int advance(int docId) {
        if (cachedFiids != null) {
            // Find the fiid in the correct segment
            Entry<Integer, NumericDocValues> prev = null;
            for (Entry<Integer, NumericDocValues> e : cachedFiids.entrySet()) {
                Integer docBase = e.getKey();
                if (docBase > docId) {
                    // Previous segment (the highest docBase lower than docId) is the right one
                    Integer prevDocBase = prev.getKey();
                    NumericDocValues prevDocValues = prev.getValue();
                    try {
                        prevDocValues.advanceExact(docId - prevDocBase);
                        return (int)prevDocValues.longValue(); // should change to long
					} catch (IOException e1) {
						throw BlackLabRuntimeException.wrap(e1);
					}
                }
                prev = e;
            }
            // Last segment is the right one
            Integer prevDocBase = prev.getKey();
            NumericDocValues prevDocValues = prev.getValue();
            try {
            	prevDocValues.advanceExact(docId - prevDocBase);
				return (int)prevDocValues.longValue();// should change to long
			} catch (IOException e1) {
				throw BlackLabRuntimeException.wrap(e1);
			}
        }

        // Not cached; find fiid by reading stored value from Document now
        try {
            return (int)Long.parseLong(reader.document(docId).get(fiidFieldName));
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }

    }

    public static List<FiidLookup> getList(List<Annotation> annotations, IndexReader reader) {
        if (annotations == null)
            return null; // HitPoperty.needsContext() can return null
        List<FiidLookup> fiidLookups = new ArrayList<>();
        for (Annotation annotation: annotations) {
            fiidLookups.add(annotation == null ? null : new FiidLookup(reader, annotation));
        }
        return fiidLookups;
    }
}

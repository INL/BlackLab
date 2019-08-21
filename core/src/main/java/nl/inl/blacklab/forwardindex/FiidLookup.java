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
import org.apache.lucene.uninverting.UninvertingReader;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * Class for looking up forward index id, using DocValues or stored fields.
 */
public class FiidLookup {

    /**
     * Index reader, for getting documents (for translating from Lucene doc id to
     * fiid)
     */
    private IndexReader reader;

    /**
     * fiid field name in the Lucene index (for translating from Lucene doc id to
     * fiid)
     */
    private String fiidFieldName;

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
                    fields.put(fiidFieldName, UninvertingReader.Type.INTEGER);
                    @SuppressWarnings("resource")
                    UninvertingReader uninv = new UninvertingReader(r, fields);
                    numericDocValues = uninv.getNumericDocValues(fiidFieldName);
                }
                if (numericDocValues != null) {
                    cachedFiids.put(rc.docBase, numericDocValues);
                }
            }
            if (cachedFiids.isEmpty()) {
                // We don't actually have DocValues.
                cachedFiids = null;
            } else {
                // See if there are actual values stored
                // [this check was introduced when we used the old FieldCache, no longer necessary?]
                int numToCheck = Math.min(AnnotationForwardIndex.NUMBER_OF_CACHE_ENTRIES_TO_CHECK, reader.maxDoc());
                if (!hasFiids(numToCheck))
                    cachedFiids = null;
            }
        } catch (IOException e) {
            BlackLabRuntimeException.wrap(e);
        }
    }

    public int get(int docId) {
        if (cachedFiids != null) {
            // Find the fiid in the correct segment
            Entry<Integer, NumericDocValues> prev = null;
            for (Entry<Integer, NumericDocValues> e : cachedFiids.entrySet()) {
                Integer docBase = e.getKey();
                if (docBase > docId) {
                    // Previous segment (the highest docBase lower than docId) is the right one
                    Integer prevDocBase = prev.getKey();
                    NumericDocValues prevDocValues = prev.getValue();
                    return (int)prevDocValues.get(docId - prevDocBase);
                }
                prev = e;
            }
            // Last segment is the right one
            Integer prevDocBase = prev.getKey();
            NumericDocValues prevDocValues = prev.getValue();
            return (int)prevDocValues.get(docId - prevDocBase);
        }

        // Not cached; find fiid by reading stored value from Document now
        try {
            return (int)Long.parseLong(reader.document(docId).get(fiidFieldName));
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
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

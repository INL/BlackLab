package nl.inl.blacklab.forwardindex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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

    /** Any cached mappings from Lucene docId to forward index id (fiid) or null if not using cache */
    private Map<Integer, Long> docIdToFiidCache;

    public FiidLookup(IndexReader reader, Annotation annotation, boolean enableRandomAccess) {
        this.reader = reader;
        this.fiidFieldName = annotation.forwardIndexIdField();
        this.docIdToFiidCache = enableRandomAccess ? new HashMap<>() : null;
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
            throw BlackLabRuntimeException.wrap(e);
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
    public int get(int docId) {
        if (docIdToFiidCache != null) {
            // We've previouslt seen this doc id; return the fiid from the cache.
            Long fiid = docIdToFiidCache.get(docId);
            if (fiid != null)
                return (int)(long)fiid;
        }

        if (cachedFiids != null) {
            // Find the fiid in the correct segment
            Entry<Integer, NumericDocValues> prev = null;
            for (Entry<Integer, NumericDocValues> e : cachedFiids.entrySet()) {
                Integer docBase = e.getKey();
                if (docBase > docId) {
                    // Previous segment (the highest docBase lower than docId) is the right one
                    Integer prevDocBase = prev.getKey();
                    NumericDocValues prevDocValues = prev.getValue();
                    return getFiidFromDocValues(prevDocBase, prevDocValues, docId);
                }
                prev = e;
            }
            // Last segment is the right one
            Integer prevDocBase = prev.getKey();
            NumericDocValues prevDocValues = prev.getValue();
            return getFiidFromDocValues(prevDocBase, prevDocValues, docId);
        }

        // Not cached; find fiid by reading stored value from Document now
        try {
            long v = Long.parseLong(reader.document(docId).get(fiidFieldName));
            if (docIdToFiidCache != null) {
                docIdToFiidCache.put(docId, v);
            }
            return (int)v;
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    /**
     * Get the requested forward index id from the DocValues object.
     *
     * Optionally caches any skipped values for later.
     *
     * @param docBase doc base for this segement
     * @param docValues doc values for this segment
     * @param docId document to get the fiid for
     * @return forward index id
     */
    private int getFiidFromDocValues(int docBase, NumericDocValues docValues, int docId) {
        try {
            if (docIdToFiidCache == null) {
                // Not caching (because we know our docIds are always increasing)
                docValues.advanceExact(docId - docBase);
                return (int) docValues.longValue();
            } else {
                // Caching; gather all fiid values in our cache until we find the requested one.
                do {
                    docValues.nextDoc();
                    docIdToFiidCache.put(docValues.docID() + docBase, docValues.longValue());
                    if (docValues.docID() == docId - docBase) {
                        // Requested docvalue found.
                        return (int) docValues.longValue();
                    }
                } while (docValues.docID() <= docId - docBase);
                throw new BlackLabRuntimeException("not found in docvalues");
            }
        } catch (IOException e1) {
            throw BlackLabRuntimeException.wrap(e1);
        }
    }

    public static List<FiidLookup> getList(List<Annotation> annotations, IndexReader reader, boolean enableRandomAccess) {
        if (annotations == null)
            return null; // HitPoperty.needsContext() can return null
        List<FiidLookup> fiidLookups = new ArrayList<>();
        for (Annotation annotation: annotations) {
            fiidLookups.add(annotation == null ? null : new FiidLookup(reader, annotation, enableRandomAccess));
        }
        return fiidLookups;
    }
}

package nl.inl.blacklab.codec;

import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;

/**
 * Quickly look up which LeafReader a docId occurs in.
 *
 * Used by the global ForwardIndex and ContentStore implementations.
 */
public class LeafReaderLookup {

    private final Map<Integer, LeafReaderContext> leafReadersByDocBase = new TreeMap<>();

    public LeafReaderLookup(IndexReader indexReader) {
        for (LeafReaderContext rc: indexReader.leaves()) {
            leafReadersByDocBase.put(rc.docBase, rc);
        }
    }

    /**
     * Find the leafReader a given docId occurs in.
     *
     * @param docId (global) docId we're looking for
     * @return matching leafReaderContext, which gives us the leaf reader and docBase
     */
    public LeafReaderContext forDocId(int docId) {
        Entry<Integer, LeafReaderContext> prev = null;
        for (Entry<Integer, LeafReaderContext> e: leafReadersByDocBase.entrySet()) {
            Integer docBase = e.getKey();
            if (docBase > docId) {
                // Previous segment (the highest docBase lower than docId) is the right one
                assert prev != null;
                return prev.getValue();
            }
            prev = e;
        }
        // Last segment is the right one
        assert prev != null;
        return prev.getValue();
    }
}

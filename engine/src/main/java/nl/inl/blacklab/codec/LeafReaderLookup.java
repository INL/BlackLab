package nl.inl.blacklab.codec;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;

/**
 * Quickly look up which LeafReader a docId occurs in.
 *
 * Used by the global ForwardIndex and ContentStore implementations.
 */
public class LeafReaderLookup extends SegmentLookup<LeafReaderContext> {
    public LeafReaderLookup(IndexReader reader) {
        super(reader.leaves(), lrc -> lrc.docBase);
    }
}

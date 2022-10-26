package nl.inl.blacklab.search.fimatch;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;
import org.eclipse.collections.api.set.primitive.MutableIntSet;

import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * Allows the forward index matching subsystem to access the forward indices,
 * including an easy and fast way to read any annotation at any position from a
 * document.
 *
 * {@link #getForwardIndexAccessorLeafReader(LeafReaderContext)} is threadsafe.
 * The other methods are not, but are called from a single thread while initializing
 * the NFA matching process (see {@link nl.inl.blacklab.search.lucene.SpanQueryFiSeq#createWeight(IndexSearcher, ScoreMode, float)}).
 */
public interface ForwardIndexAccessor {

    /**
     * Get the index number corresponding to the given annotation name.
     *
     * @param annotationName annotation to get the index for
     * @return index for this annotation
     */
    int getAnnotationNumber(String annotationName);

    /**
     * Get the term number for a given term string.
     *
     * @param results (out) term number for this term in this annotation
     * @param annotationNumber which annotation to get term number for
     * @param annotationValue which term string to get term number for
     * @param sensitivity match sensitively or not? (currently both or neither)
     */
    void getGlobalTermNumbers(MutableIntSet results, int annotationNumber, String annotationValue,
            MatchSensitivity sensitivity);

    /**
     * Get an accessor for forward index documents from this leafreader.
     *
     * The returned accessor may not be threadsafe, which is okay, because it is only used
     * from Spans (which are always single-threaded).
     *
     * @param readerContext index reader
     * @return reader-specific accessor
     */
    ForwardIndexAccessorLeafReader getForwardIndexAccessorLeafReader(LeafReaderContext readerContext);
}

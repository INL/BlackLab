package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Objects;

import org.apache.lucene.search.spans.Spans;
import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

/**
 * Each bucket contains all the matches in a single document, startpoint sorted.
 *
 * There's also an index of startpoints to the matches that start there, so we
 * can quickly find the matches that start at a certain position. This is useful
 * for SpansRepetition, which needs to find connecting matches from the same clause.
 */
class SpansInBucketsPerDocumentWithStartpointIndex extends SpansInBucketsPerDocument {

    /** For each start position, a list of match indexes in the bucket that start there. */
    MutableIntObjectMap<MutableIntList> startpointIndex = IntObjectMaps.mutable.empty();

    public SpansInBucketsPerDocumentWithStartpointIndex(BLSpans source) {
        super(source);
    }

    @Override
    protected void gatherHits() throws IOException {
        do {
            addHitFromSource();
            int index = bucketSize() - 1;
            int start = startPosition(index);
            startpointIndex.getIfAbsentPut(start, IntArrayList::new).add(index);
        } while (source.nextStartPosition() != Spans.NO_MORE_POSITIONS);
    }

    /**
     * Get the indexes of the matches that start at the specified position.
     *
     * @param start start position
     * @return indexes of the matches that start at the specified position (may be empty)
     */
    IntList indexesForStartpoint(int start) {
        return Objects.requireNonNullElse(startpointIndex.get(start), IntLists.immutable.empty());
    }

}

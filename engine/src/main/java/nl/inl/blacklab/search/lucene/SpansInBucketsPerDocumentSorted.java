package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntComparator;

/**
 * Wrap a Spans to retrieve hits per document, so we can process all matches in
 * a document efficiently.
 *
 * Hits are sorted by the given comparator.
 */
class SpansInBucketsPerDocumentSorted extends SpansInBucketsPerDocument {

    private static int compareEnds(long a, long b) {
        int ea = (int)a;
        int eb = (int)b;
        if (ea == eb) {
            // Identical end points; start point is tiebreaker
            return (int)(a >> 32) - (int)(b >> 32);
        }
        return ea - eb;
    }

    private final boolean sortByStartPoint;

    private final IntArrayList sortIndexes = new IntArrayList(LIST_INITIAL_CAPACITY);

    private final IntComparator cmpStartPoint = (i1, i2) -> {
        long a = startsEnds.getLong(i1);
        long b = startsEnds.getLong(i2);
        return Long.compare(a, b);
    };

    private final IntComparator cmpEndPoint = (i1, i2) -> {
        long a = startsEnds.getLong(i1);
        long b = startsEnds.getLong(i2);
        return compareEnds(a, b);
    };

    private final SpanGuaranteesAdapter guarantees;

    public SpansInBucketsPerDocumentSorted(BLSpans source, boolean sortByStartPoint) {
        super(source);
        this.sortByStartPoint = sortByStartPoint;
        this.guarantees = new SpanGuaranteesAdapter(source.guarantees()) {
            @Override
            public boolean hitsStartPointSorted() {
                return sortByStartPoint || source.guarantees().hitsAllSameLength();
            }

            @Override
            public boolean hitsEndPointSorted() {
                return !sortByStartPoint || source.guarantees().hitsAllSameLength();
            }
        };
    }

    @Override
    protected void gatherHits() throws IOException {
        super.gatherHits();

        // Sort by start- or endpoint
        initSortIndexes();
        IntArrays.quickSort(sortIndexes.elements(), 0, sortIndexes.size(), sortByStartPoint ? cmpStartPoint : cmpEndPoint);
    }

    private void initSortIndexes() {
        sortIndexes.clear();
        sortIndexes.ensureCapacity(startsEnds.size());
        for (int i = 0; i < startsEnds.size(); i++)
            sortIndexes.add(i);
    }

    @Override
    public int startPosition(int indexInBucket) {
        return super.startPosition(sortIndexes.getInt(indexInBucket));
    }

    @Override
    public int endPosition(int indexInBucket) {
        return super.endPosition(sortIndexes.getInt(indexInBucket));
    }

    @Override
    public void getMatchInfo(int indexInBucket, MatchInfo[] matchInfo) {
        super.getMatchInfo(sortIndexes.getInt(indexInBucket), matchInfo);
    }

    @Override
    public MatchInfo getRelationInfo(int indexInBucket) {
        return super.getRelationInfo(sortIndexes.getInt(indexInBucket));
    }

    @Override
    public SpanGuarantees guarantees() {
        return this.guarantees;
    }
}

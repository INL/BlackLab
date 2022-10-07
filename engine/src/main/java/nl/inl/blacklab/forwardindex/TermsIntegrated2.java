package nl.inl.blacklab.forwardindex;

import java.io.IOException;
import java.text.CollationKey;
import java.text.Collator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;

import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntComparator;
import nl.inl.blacklab.codec.BLTerms;
import nl.inl.blacklab.codec.BlackLab40PostingsReader;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.util.BlockTimer;

/** Keeps a list of unique terms and their sort positions.
 *
 * This version is integrated into the Lucene index.
 */
public class TermsIntegrated2 extends TermsReaderAbstract {

    private final Map<String, CollationKey> collationCacheSensitive = new HashMap<>();
    private final Map<String, CollationKey> collationCacheInsensitive = new HashMap<>();

    /** Information about a term in the index, and the sort positions in each segment
     *  it occurs in. We'll use this to speed up comparisons where possible (comparing
     *  sort positions in one of the segments is much faster than calculating CollationKeys).
     */
    public class TermInIndex implements Comparable<TermInIndex> {
        /** Term string */
        String term;

        /** This term's global id */
        int globalTermId;

        /** Sort position within each segment, case-sensitive */
        int[] segmentPosSensitive;

        /** Sort position within each segment, case-insensitive */
        int[] segmentPosInsensitive;

        public TermInIndex(String term, int globalTermId, int numberOfSegments) {
            this.term = term;
            this.globalTermId = globalTermId;
            segmentPosSensitive = new int[numberOfSegments];
            Arrays.fill(segmentPosSensitive, -1);
            segmentPosInsensitive = new int[numberOfSegments];
            Arrays.fill(segmentPosInsensitive, -1);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof TermInIndex))
                return false;
            TermInIndex that = (TermInIndex) o;
            return globalTermId == that.globalTermId;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(globalTermId);
        }

        @Override
        public int compareTo(TermInIndex other) {
            int[] pa, pb;

            if (compareSensitive) {
                pa = segmentPosSensitive;
                pb = other.segmentPosSensitive;
            } else {
                pa = segmentPosInsensitive;
                pb = other.segmentPosInsensitive;
            }
            // See if there's a segment these two terms both occur in.
            // If so, we already know how these terms compare.
            for (int i = 0; i < pa.length; i++) {
                int a = pa[i], b = pb[i];
                if (a >= 0 && b >= 0) {
                    // Both terms occur in this segment.
                    // Their relative ordering in that segment applies here as well.
                    return Integer.compare(a, b);
                }
            }
            // There are no segments that these terms both occur in.
            Collator collator = compareSensitive ? TermsIntegrated2.this.collator : collatorInsensitive;
            Map<String, CollationKey> cache = compareSensitive ? collationCacheSensitive : collationCacheInsensitive;

            CollationKey a = cache.computeIfAbsent(term, __ -> collator.getCollationKey(term));
            CollationKey b = cache.computeIfAbsent(term, __ -> collator.getCollationKey(other.term));

            return a.compareTo(b);
        }

        public int globalId() {
            return globalTermId;
        }
    }

    private final IndexReader indexReader;

    private final String luceneField;

    /** Per segment (by ord number): the translation of that segment's term ids to
     *  global term ids.
     *  Hopefully eventually no longer needed.
     */
    private final Map<Integer, int[]> segmentToGlobalTermIds = new HashMap<>();

    /** Are we sorting TermInIndex sensitively or insensitively right now? */
    private boolean compareSensitive = true;

    public TermsIntegrated2(Collators collators, IndexReader indexReader, String luceneField) {
        super(collators);
        this.indexReader = indexReader;
        this.luceneField = luceneField;

        try (BlockTimer timer = BlockTimer.create("Term loading+merging (" + luceneField + ")")) {
            // Get all the terms by enumerating the terms enum for each segment.
//            System.err.println(System.currentTimeMillis() + "    read terms " + luceneField);
            TermInIndex[] terms;
            try (BlockTimer __ = timer.child("Reading from disk")) {
                terms = readTermsFromIndex();
            }

            String[] termStrings;
            int[] termId2SensitivePosition;
            int[] termId2InsensitivePosition;
            try (BlockTimer __ = timer.child("sorting")) {
                // Determine the sort orders for the terms
    //            System.err.println(System.currentTimeMillis() + "    determine sort 1");
                int[] sortedSensitive = determineSort(terms, true);
    //            System.err.println(System.currentTimeMillis() + "    determine sort 2");
                int[] sortedInsensitive = determineSort(terms, false);

                // Process the values we've determined so far the same way as with the external forward index.
    //            System.err.println(System.currentTimeMillis() + "    invert 1");
                termId2SensitivePosition = invert(terms, sortedSensitive, true);
    //            System.err.println(System.currentTimeMillis() + "    invert 2");
                termId2InsensitivePosition = invert(terms, sortedInsensitive, false);
                // TODO: just keep terms in String[] and have the sort arrays separately to avoid this conversion?
    //            System.err.println(System.currentTimeMillis() + "    to array");
                termStrings = Arrays.stream(terms).map(t -> t.term).toArray(String[]::new);
    //            System.err.println(System.currentTimeMillis() + "    finish");
            }

            finishInitialization(termStrings, termId2SensitivePosition, termId2InsensitivePosition);

        }
    }

    // globalTermID -> int[] sensitive per segment
    // globalTermID -> int[] insensitive per segment
    // globalTermID -> string


    private TermInIndex[] readTermsFromIndex() {
        // A list of globally unique terms that occur in our index.
        Map<String, TermInIndex> globalTermIds = new LinkedHashMap<>(); // global term ids, in the correct order
        try {
            List<LeafReaderContext> leaves = indexReader.leaves();
            for (LeafReaderContext l: leaves) {
                readTermsFromSegment(globalTermIds, l);
            }
            return globalTermIds.values().toArray(TermInIndex[]::new);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void readTermsFromSegment(Map<String, TermInIndex> globalTermIds, LeafReaderContext lrc) throws IOException {
        BlackLab40PostingsReader r = BlackLab40PostingsReader.get(lrc);
        TermsIntegratedSegment s = new TermsIntegratedSegment(r, luceneField, lrc.ord);
        BLTerms segmentTerms = (BLTerms) lrc.reader().terms(luceneField);
        if (segmentTerms != null) { // can happen if segment only contains index metadata doc
            segmentTerms.setTermsIntegrated(this, lrc.ord);
        }

        TermsIntegratedSegment.TermInSegmentIterator2 it = s.iterator();
        int [] segmentToGlobal = segmentToGlobalTermIds.computeIfAbsent(it.ord(), __ -> new int[it.size()]);
        while (it.hasNext()) {
            TermsIntegratedSegment.TermInSegment2 t = it.next();
            TermInIndex tii = globalTermIds.computeIfAbsent(t.term, __ -> termInIndex(t.term, globalTermIds.size()));
            // Remember the mapping from segment id to global id
            segmentToGlobal[t.id] = tii.globalTermId;
            // Remember the sort position of this term in this segment, to save time comparing later
            tii.segmentPosSensitive[it.ord()] = t.sortPositionSensitive;
            tii.segmentPosInsensitive[it.ord()] = t.sortPositionInsensitive;
        }

        s.close();
    }

    private int[] determineSort(TermInIndex[] terms, boolean sensitive) {
        compareSensitive = sensitive;
        int[] sorted = new int[terms.length];
        for (int i = 0; i < terms.length; i++) {
            sorted[i] = i;
        }
        IntArrays.quickSort(sorted, (a, b) -> terms[a].compareTo(terms[b]));
        return sorted;
    }

    /**
     * Invert the given array so the values become the indexes and vice versa.
     *
     * @param array array to invert
     * @return inverted array
     */
    private int[] invert(TermInIndex[] terms, int[] array, boolean sensitive) {
        compareSensitive = sensitive;
        int[] result = new int[array.length];
        int prevSortPosition = -1;
        int prevTermId = -1;
        for (int i = 0; i < array.length; i++) {
            int termId = array[i];
            int sortPosition = i;
            if (prevTermId >= 0 && terms[prevTermId].compareTo(terms[termId]) == 0) {
                // Keep the same sort position because the terms are the same
                sortPosition = prevSortPosition;
            } else {
                // Remember the sort position in case the next term is identical
                prevSortPosition = sortPosition;
            }
            result[termId] = sortPosition;
            prevTermId = termId;
        }
        return result;
    }

    @Override
    public int[] segmentIdsToGlobalIds(int ord, int[] snippet) {
        int[] mapping = segmentToGlobalTermIds.get(ord);
        int[] converted = new int[snippet.length];
        for (int i = 0; i < snippet.length; i++) {
            converted[i] = snippet[i] < 0 ? snippet[i] : mapping[snippet[i]];
        }
        return converted;
    }

    public int segmentIdToGlobalId(int ord, int id) {
        int[] mapping = segmentToGlobalTermIds.get(ord);
        return id < 0 ? id : mapping[id];
    }

    public TermInIndex termInIndex(String term, int globalTermId) {
        return new TermInIndex(term, globalTermId, indexReader.leaves().size());
    }
}

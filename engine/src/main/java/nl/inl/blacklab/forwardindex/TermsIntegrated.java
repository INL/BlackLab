package nl.inl.blacklab.forwardindex;

import java.io.IOException;
import java.text.CollationKey;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.codec.BLTerms;
import nl.inl.blacklab.codec.BlackLab40PostingsReader;
import nl.inl.util.BlockTimer;

/** Keeps a list of unique terms and their sort positions.
 *
 * This version is integrated into the Lucene index.
 */
public class TermsIntegrated extends TermsReaderAbstract {

    /** Information about a term in the index, and the sort positions in each segment
     *  it occurs in. We'll use this to speed up comparisons where possible (comparing
     *  sort positions in one of the segments is much faster than calculating CollationKeys).
     */
    public class TermInIndex implements Comparable<TermInIndex> {
        /** Term string */
        String term;

        CollationKey ckSensitive;

        CollationKey ckInsensitive;

        /** This term's global id */
        int globalTermId;

        public TermInIndex(String term, int globalTermId) {
            this.term = term;
            this.globalTermId = globalTermId;
            ckSensitive = collator.getCollationKey(term);
            ckInsensitive = collatorInsensitive.getCollationKey(term);
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
            return compareSensitive ? ckSensitive.compareTo(other.ckSensitive) :
                    ckInsensitive.compareTo(other.ckInsensitive);
        }

        public int globalId() {
            return globalTermId;
        }
    }

    private IndexReader indexReader;

    private final String luceneField;

    /** Per segment (by ord number): the translation of that segment's term ids to
     *  global term ids.
     *  Hopefully eventually no longer needed.
     */
    private final Map<Integer, int[]> segmentToGlobalTermIds = new HashMap<>();

    /** Are we sorting TermInIndex sensitively or insensitively right now? */
    private boolean compareSensitive = true;

    public TermsIntegrated(Collators collators, IndexReader indexReader, String luceneField) {
        super(collators);
        try (BlockTimer bt = BlockTimer.create("Determine " + luceneField + " terms list")) {
            this.indexReader = indexReader;
            this.luceneField = luceneField;
            TermInIndex[] terms;
            try (BlockTimer bt2 = BlockTimer.create(luceneField + ": readTermsFromIndex")) {
                terms = readTermsFromIndex();
            }

            // Determine the sort orders for the terms
            int[] sortedSensitive;
            try (BlockTimer bt2 = BlockTimer.create(luceneField + ": determineSort sensitive")) {
                sortedSensitive = determineSort(terms, true);
            }
            int[] sortedInsensitive;
            try (BlockTimer bt2 = BlockTimer.create(luceneField + ": determineSort insensitive")) {
                sortedInsensitive = determineSort(terms, false);
            }

            // Process the values we've determined so far the same way as with the external forward index.
            int[] termId2SensitivePosition;
            try (BlockTimer bt2 = BlockTimer.create(luceneField + ": invert sensitive")) {
                termId2SensitivePosition = invert(terms, sortedSensitive, true);
            }
            int[] termId2InsensitivePosition;
            try (BlockTimer bt2 = BlockTimer.create(luceneField + ": invert insensitive")) {
                termId2InsensitivePosition = invert(terms, sortedInsensitive, false);
            }

            String[] termStrings;
            try (BlockTimer bt2 = BlockTimer.create(luceneField + ": extract terms strings")) {
                // OPT: just keep terms in String[] and have the sort arrays separately to avoid this conversion?
                termStrings = Arrays.stream(terms).map(t -> t.term).toArray(String[]::new);
            }

            try (BlockTimer bt2 = BlockTimer.create(luceneField + ": finishInitialization")) {
                finishInitialization(luceneField, termStrings, termId2SensitivePosition, termId2InsensitivePosition);
            }

            // clear temporary variables
            this.indexReader = null;
        }
    }


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
        BLTerms segmentTerms = (BLTerms) lrc.reader().terms(luceneField);
        if (segmentTerms == null) {
            // can happen if segment only contains index metadata doc
            return;
        }
        segmentTerms.setTermsIntegrated(this, lrc.ord);
        BlackLab40PostingsReader r = BlackLab40PostingsReader.get(lrc);
        TermsIntegratedSegment s = new TermsIntegratedSegment(r, luceneField, lrc.ord);

        Iterator<TermsIntegratedSegment.TermInSegment> it = s.iterator();
        int [] segmentToGlobal = segmentToGlobalTermIds.computeIfAbsent(s.ord(), __ -> new int[s.size()]);
        while (it.hasNext()) {
            TermsIntegratedSegment.TermInSegment t = it.next();
            TermInIndex tii = globalTermIds.computeIfAbsent(t.term, __ -> termInIndex(t.term, globalTermIds.size()));
            // Remember the mapping from segment id to global id
            segmentToGlobal[t.id] = tii.globalTermId;
        }

        s.close();
    }

    private int[] determineSort(TermInIndex[] terms, boolean sensitive) {
        compareSensitive = sensitive; // make sure we use the correct comparison

        // Initialize array of indexes to be sorted
        int[] sorted = new int[terms.length];
        for (int i = 0; i < terms.length; i++) {
            sorted[i] = i;
        }

        // Below is about 5% faster than FastUtil's IntArrays.parallelQuickSort() for very large arrays
        ParallelIntSorter.parallelSort(sorted, (a, b) -> terms[a].compareTo(terms[b]));

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

    private TermInIndex termInIndex(String term, int globalTermId) {
        return new TermInIndex(term, globalTermId);
    }
}

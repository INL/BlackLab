package nl.inl.blacklab.forwardindex;

import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;

import it.unimi.dsi.fastutil.ints.IntArrays;
import nl.inl.blacklab.codec.BLTerms;

/** Keeps a list of unique terms and their sort positions.
 *
 * This version is integrated into the Lucene index.
 */
public class TermsIntegrated extends TermsReaderAbstract {

    private final IndexReader indexReader;

    private final String luceneField;

    /** Per segment (by ord number): the translation of that segment's term ids to
     *  global term ids.
     *  Hopefully eventually no longer needed.
     */
    private final Map<Integer, List<Integer>> segmentToGlobalTermIds = new HashMap<>();

    public TermsIntegrated(Collators collators, IndexReader indexReader, String luceneField) {
        super(collators);
        this.indexReader = indexReader;
        this.luceneField = luceneField;

        // Get all the terms by enumerating the terms enum for each segment.
        String[] terms = readTermsFromIndex();

        // Determine the sort orders for the terms
        int[] sortedSensitive = determineSort(terms, collator);
        int[] sortedInsensitive = determineSort(terms, collatorInsensitive);

        // Process the values we've determined so far the same way as with the external forward index.
        int[] termId2SensitivePosition = invert(Arrays.asList(terms), sortedSensitive, collator);
        int[] termId2InsensitivePosition = invert(Arrays.asList(terms), sortedInsensitive, collatorInsensitive);
        finishInitialization(terms, termId2SensitivePosition, termId2InsensitivePosition);
    }

    private String[] readTermsFromIndex() {
        // A list of globally unique terms that occur in our index.
        Map<String, Integer> globalTermIds = new LinkedHashMap<>(); // global term ids, in the correct order
        try {
            for (LeafReaderContext l: indexReader.leaves()) {
                BLTerms terms = ((BLTerms)l.reader().terms(luceneField));
                if (terms == null) continue; // can happen if segment only contains index metadata doc

                terms.setTermsIntegrated(this, l.ord);
                List<Integer> mapping = getSegmentToGlobalMapping(terms, globalTermIds);
                this.segmentToGlobalTermIds.put(l.ord, mapping);
            }
            return globalTermIds.keySet().toArray(String[]::new);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    
    /**
     * Read the terms in the segment, and return a mapping to global
     *
     * If a new term is found, it is added to the global term map. If the term
     * occurred before, the existing term id is used.
     *
     * @param segment
     * @param globalTermIds map of term string to global term id
     * @return list mapping term ids in this segment to global term id
     * @throws IOException
     */
    public static List<Integer> getSegmentToGlobalMapping(BLTerms segment, Map<String, Integer> globalTermIds) throws IOException {
        List<Integer> thisSegmentToGlobal = new ArrayList<>();
        BytesRef termBytes;
        TermsEnum it = segment.iterator();
        while ((termBytes = it.next()) != null) {
            String term = termBytes.utf8ToString().intern(); // save memory by avoiding duplicates
            // Determine global term id, get existing if present, else assign the next ID.
            int globalTermId = globalTermIds.computeIfAbsent(term, __ -> globalTermIds.size());
            // Keep track of mapping from this segment's term id to global term id
            thisSegmentToGlobal.add(globalTermId);
        }
        return thisSegmentToGlobal;
    }

    private int[] determineSort(String[] terms, Collator collator) {
        int[] sorted = new int[terms.length];
        for (int i = 0; i < terms.length; i++) {
            sorted[i] = i;
        }
        IntArrays.quickSort(sorted, (a, b) -> collator.compare(terms[a], terms[b]));
        return sorted;
    }

    /**
     * Invert the given array so the values become the indexes and vice versa.
     *
     * @param array array to invert
     * @return inverted array
     */
    public static int[] invert(List<String> terms, int[] array, Collator collator) {
        int[] result = new int[array.length];
        int prevSortPosition = -1;
        int prevTermId = -1;
        for (int i = 0; i < array.length; i++) {
            int termId = array[i];
            int sortPosition = i;
            if (prevTermId >= 0 && collator.equals(terms.get(prevTermId), terms.get(termId))) {
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
        List<Integer> mapping = segmentToGlobalTermIds.get(ord);
        int[] converted = new int[snippet.length];
        for (int i = 0; i < snippet.length; i++) {
            converted[i] = snippet[i] < 0 ? snippet[i] : mapping.get(snippet[i]);
        }
        return converted;
    }

    public int segmentIdToGlobalId(int ord, int id) {
        List<Integer> mapping = segmentToGlobalTermIds.get(ord);
        return id < 0 ? id : mapping.get(id);
    }
}

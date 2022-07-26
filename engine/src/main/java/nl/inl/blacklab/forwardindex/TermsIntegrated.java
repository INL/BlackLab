package nl.inl.blacklab.forwardindex;

import java.io.IOException;
import java.text.Collator;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;

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
        Integer[] sortedSensitive = determineSort(terms, collator);
        Integer[] sortedInsensitive = determineSort(terms, collatorInsensitive);

        // Process the values we've determined so far the same way as with the external forward index.
        int[] termId2SensitivePosition = invert(terms, sortedSensitive, collator);
        int[] termId2InsensitivePosition = invert(terms, sortedInsensitive, collatorInsensitive);
        finishInitialization(terms, termId2SensitivePosition, termId2InsensitivePosition);
    }

    private String[] readTermsFromIndex() {
        // A list of globally unique terms that occur in our index.
        Map<String, Integer> globalTermIds = new LinkedHashMap<>(); // global term ids, in the correct order
        try {
            List<LeafReaderContext> leaves = indexReader.leaves();
            for (LeafReaderContext l: leaves) {
                BLTerms segmentTerms = (BLTerms)l.reader().terms(luceneField);
                segmentTerms.setTermsIntegrated(this, l.ord);
                segmentToGlobalTermIds.put(l.ord, segmentTerms.getSegmentToGlobalMapping(globalTermIds));
            }
            return globalTermIds.keySet().toArray(String[]::new);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Integer[] determineSort(String[] terms, Collator collator) {
        Integer[] sorted = new Integer[terms.length];
        for (int i = 0; i < terms.length; i++) {
            sorted[i] = i;
        }
        Arrays.sort(sorted, (a, b) -> collator.compare(terms[a], terms[b]));
        return sorted;
    }

    /**
     * Invert the given array so the values become the indexes and vice versa.
     *
     * @param array array to invert
     * @return inverted array
     */
    private int[] invert(String[] terms, Integer[] array, Collator collator) {
        int[] result = new int[array.length];
        int prevSortPosition = -1;
        int prevTermId = -1;
        for (int i = 0; i < array.length; i++) {
            int termId = array[i];
            int sortPosition = i;
            if (prevTermId >= 0 && collator.compare(terms[prevTermId], terms[termId]) == 0) {
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

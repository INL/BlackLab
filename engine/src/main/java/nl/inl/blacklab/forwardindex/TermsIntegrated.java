package nl.inl.blacklab.forwardindex;

import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.codec.BlackLab40PostingsReader;
import nl.inl.blacklab.forwardindex.TermsIntegratedSegment.TermInSegment;
import nl.inl.blacklab.forwardindex.TermsIntegratedSegment.TermInSegmentIterator;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

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
    private final Map<Integer, int[]> segmentToGlobalTermIds = new HashMap<>();

    public TermsIntegrated(Collators collators, IndexReader indexReader, String luceneField) {
        super(collators);
        this.indexReader = indexReader;
        this.luceneField = luceneField;

        List<TermsIntegratedSegment> termsPerSegment = new ArrayList<>();
        for (LeafReaderContext lrc : indexReader.leaves()) {
            BlackLab40PostingsReader r = BlackLab40PostingsReader.get(lrc);
            TermsIntegratedSegment s = new TermsIntegratedSegment(r, luceneField, lrc.ord);
            termsPerSegment.add(s);
        }

        try {
            readFromAndMerge(termsPerSegment);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load terms", e);
        }
    }


    /**
     * Get a sorted list of all the segments, as represented by their iterators:
     * <pre>
     * 
     * Imagine we have 3 segments, with two terms each:
     * 
     * segment ordinal -->  | 1    2   3 
     *                      | ----------
     * term at index    [0] | a    b   a 
     *                  [1] | a    c   d
     * 
     * We add all these segments to the queue,  
     * the queue will sort these segments in ascending order. 
     * Note the swapping of places of ordinal 2 and 3, 
     * as the first term in segment 3 is 'a', which comes before the first term of segment 2 ('b').
     * 
     * index in the priorityQueue: | [0]     [1]     [2]
     * segment ordinal             |  1       3       2
     *                             |  -----------------
     * contents                [0] |  a       a       b
     *                         [1] |  a       d       c
     * 
     * 
     * queue.poll() will pop the segment at index [0].
     * queue.add() will re-sort the segments based on their first (unread) term.
     * 
     * So we can now walk all terms across all segments in a globally ascending order by continuously calling poll() and insert().
     * 
     * </pre>
     * @param segments
     * @param coll
     * @param sensitivity
     * @return
     */
    private static PriorityQueue<TermInSegmentIterator> getQueue(List<TermsIntegratedSegment> segments, Collator coll, MatchSensitivity sensitivity) {
        // Collator coll = collators.get(sensitivity);
        PriorityQueue<TermInSegmentIterator> q = new PriorityQueue<>((itA,itB) -> {
            String a = itA.peek().term;
            String b = itB.peek().term;
            return a.equals(b) ? 0 : coll.compare(a, b);
        });
        for (TermsIntegratedSegment s : segments) q.add(s.iterator(sensitivity));
        return q;
    }

    private void readFromAndMerge(List<TermsIntegratedSegment> segments) throws IOException {
        // what do we need to perform
        // generate the following fields:
        // x segmentToGlobalTermIds
        // x (global) terms (global list, any order as long as the sort arrays are correct)
        // x (global) termId2SensitivePosition
        // x (global) termId2InsensitivePosition

        // todo use Collators instead of collator+collatorInsensitive?
        PriorityQueue<TermInSegmentIterator> q = getQueue(segments, collator, MatchSensitivity.SENSITIVE);
        
        // Store which terms we've already seen, along with the global ID we assigned them.
        Map<String, Integer> term2GlobalID = new LinkedHashMap<>();
        

        while (!q.isEmpty()) {
            // get the next term in the next segment, don't forget to re-add the segment to the queue if it has more terms.
            TermInSegmentIterator i = q.poll();
            TermInSegment t = i.next();
            if (i.hasNext()) q.add(i);

            int segmentID = i.ord();
            int localID = t.id;
            int globalID = term2GlobalID.computeIfAbsent(t.term, __ -> term2GlobalID.size());
            segmentToGlobalTermIds.computeIfAbsent(segmentID, __ -> new int[i.size()])[localID] = globalID;
        }


        // let's create the insensitive order
        q = getQueue(segments, collatorInsensitive, MatchSensitivity.INSENSITIVE);

        int[] termId2InsensitivePosition = new int[term2GlobalID.size()];

        String prevTerm = null;
        int insensitivePosition = -1;
        while (!q.isEmpty()) {
            TermInSegmentIterator i = q.poll();
            TermInSegment t = i.next();
            if (i.hasNext()) q.add(i);

            String term = t.term;
            int globalID = term2GlobalID.get(term);
            
            // if terms are considered equal in a case-insensitive comparison, 
            // they should be assigned the same position.
            boolean equalsPreviousTerm = prevTerm != null && collatorInsensitive.equals(prevTerm, term);
            termId2InsensitivePosition[globalID] = equalsPreviousTerm ? insensitivePosition : ++insensitivePosition;
            prevTerm = term;
        }

        // since we've added terms to the global list in sensitive order, 
        // the term's global id is now equal to the term's sensitive sort position
        // (todo this fact might enable optimizations)
        String[] terms = new String[term2GlobalID.size()];
        for (Map.Entry<String, Integer> e : term2GlobalID.entrySet()) {
            terms[e.getValue()] = e.getKey();
        }

        int[] termID2SensitivePosition = new int[terms.length];
        for (int i = 0; i < termID2SensitivePosition.length; ++i) termID2SensitivePosition[i] = i;
        
        // check the sensitive order
        List<String> terms2 = new ArrayList<>();
        for (String t : terms) terms2.add(t);
        terms2.sort(collator);

        finishInitialization(
            terms,
            termID2SensitivePosition,
            termId2InsensitivePosition
        );
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
}

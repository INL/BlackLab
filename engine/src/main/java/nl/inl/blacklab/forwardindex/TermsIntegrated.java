package nl.inl.blacklab.forwardindex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;

import gnu.trove.list.array.TIntArrayList;
import nl.inl.blacklab.codec.BlackLab40PostingsReader;
import nl.inl.blacklab.forwardindex.TermsIntegratedSegment.TermInSegment;
import nl.inl.blacklab.forwardindex.TermsIntegratedSegment.TermInSegmentIterator;

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


    private void readFromAndMerge(List<TermsIntegratedSegment> segments) throws IOException {
        // what do we need to perform
        // generate the following fields:
        // x segmentToGlobalTermIds
        // x terms (global list, any order as long as the sort arrays are correct)
        // x (global) termId2SensitivePosition
        // x (global) termId2InsensitivePosition

        for (TermsIntegratedSegment s : segments) segmentToGlobalTermIds.put(s.ord(), new int[s.field().getNumberOfTerms()]);

        Map<String, Integer> term2GlobalID = new LinkedHashMap<>();
        
        PriorityQueue<TermInSegmentIterator> q = new PriorityQueue<>((a,b) -> a.peek().term.equals(b.peek().term) ? 0 : collator.compare(a.peek().term, b.peek().term));
        for (TermsIntegratedSegment s : segments) q.add(s.iteratorSensitive());
        
        while (!q.isEmpty()) {
            TermInSegmentIterator i = q.poll();
            TermInSegment t = i.next();
            if (i.hasNext()) q.add(i);

            int segmentID = i.source().ord();
            int localID = t.id;
            int globalID = term2GlobalID.computeIfAbsent(t.term, __ -> term2GlobalID.size());
            segmentToGlobalTermIds.get(segmentID)[localID] = globalID;
        }

        // let's create the insensitive order
        int[] termId2InsensitivePosition = new int[term2GlobalID.size()];
        q = new PriorityQueue<>((a,b) -> a.peek().term.equals(b.peek().term) ? 0 : collatorInsensitive.compare(a.peek().term, b.peek().term));
        for (TermsIntegratedSegment s : segments) q.add(s.iteratorInsensitive());
        
        String prevTerm = null;
        int insensitivePosition = 0;
        while (!q.isEmpty()) {
            TermInSegmentIterator i = q.poll();
            TermInSegment t = i.next();
            if (i.hasNext()) q.add(i);

            int globalID = segmentToGlobalTermIds.get(i.source().ord())[t.id];
            String term = t.term;
            termId2InsensitivePosition[globalID] = (prevTerm == null || prevTerm.equals(term) || collatorInsensitive.equals(prevTerm, term)) ? insensitivePosition : insensitivePosition++;
        }

        int[] termID2SensitivePosition = new int[term2GlobalID.size()];
        for (int i = 0; i < termID2SensitivePosition.length; ++i) termID2SensitivePosition[i] = i;

        String[] terms = new String[term2GlobalID.size()];
        int i = 0;
        for (String s : term2GlobalID.keySet()) terms[i++] = s;

        for (TermsIntegratedSegment s : segments) s.close();

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

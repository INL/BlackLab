package nl.inl.blacklab.codec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.CompiledAutomaton;

import nl.inl.blacklab.forwardindex.TermsIntegrated;
import nl.inl.blacklab.forwardindex.TermsSegmentReader;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * Overridden version of the Lucene Terms class so we
 * can access our BLFieldsProducer from the rest of our code.
 * We need this to access the forward index.
 */
public class BLTerms extends Terms implements TermsSegmentReader {

    private final BlackLab40PostingsReader fieldsProducer;

    /** The Lucene terms object we're wrapping */
    private final Terms terms;

    /** The global terms object, which we use to implement get() and termsEqual() */
    private TermsIntegrated termsIntegrated;

    /** Our segment number */
    private int ord;

    public BLTerms(Terms terms, BlackLab40PostingsReader fieldsProducer) {
        this.terms = terms;
        this.fieldsProducer = fieldsProducer;
    }

    public BlackLab40PostingsReader getFieldsProducer() {
        return fieldsProducer;
    }

    @Override
    public TermsEnum iterator() throws IOException {
        return terms.iterator();
    }

    @Override
    public TermsEnum intersect(CompiledAutomaton compiled, BytesRef startTerm) throws IOException {
        return terms.intersect(compiled, startTerm);
    }

    @Override
    public long size() throws IOException {
        return terms.size();
    }

    @Override
    public long getSumTotalTermFreq() throws IOException {
        return terms.getSumTotalTermFreq();
    }

    @Override
    public long getSumDocFreq() throws IOException {
        return terms.getSumDocFreq();
    }

    @Override
    public int getDocCount() throws IOException {
        return terms.getDocCount();
    }

    @Override
    public boolean hasFreqs() {
        return terms.hasFreqs();
    }

    @Override
    public boolean hasOffsets() {
        return terms.hasOffsets();
    }

    @Override
    public boolean hasPositions() {
        return terms.hasPositions();
    }

    @Override
    public boolean hasPayloads() {
        return terms.hasPayloads();
    }

    @Override
    public BytesRef getMin() throws IOException {
        return terms.getMin();
    }

    @Override
    public BytesRef getMax() throws IOException {
        return terms.getMax();
    }

    @Override
    public Object getStats() throws IOException {
        return terms.getStats();
    }

    /**
     * Read and store the terms in this segment and return term mapping.
     *
     * If a new term is found, it is added to the global term map. If the term
     * occurred before, the existing term id is used.
     *
     * @param globalTermIds map of term string to global term id
     * @return list mapping term ids in this segment to global term id
     * @throws IOException
     */
    public List<Integer> getSegmentToGlobalMapping(Map<String, Integer> globalTermIds) throws IOException {
        TermsEnum ti = iterator();
        List<Integer> thisSegmentToGlobal = new ArrayList<>();
        while (true) {
            BytesRef termBytes = ti.next();
            if (termBytes == null)
                break;
            String term = termBytes.utf8ToString();
            term = term.intern(); // save memory by avoiding duplicates

            // Determine global term id
            int globalTermId;
            if (!globalTermIds.containsKey(term)) {
                globalTermId = globalTermIds.size();
                globalTermIds.put(term, globalTermId);
            } else {
                globalTermId = globalTermIds.get(term);
            }

            // Keep track of mapping from this segment's term id to global term id
            thisSegmentToGlobal.add(globalTermId);
        }
        return thisSegmentToGlobal;
    }

    @Override
    public String get(int id) {
        return termsIntegrated.get(termsIntegrated.segmentIdToGlobalId(ord, id));
    }

    @Override
    public boolean termsEqual(int[] termIds, MatchSensitivity sensitivity) {
        int[] globalTermIds = termsIntegrated.segmentIdsToGlobalIds(ord, termIds);
        return termsIntegrated.termsEqual(globalTermIds, sensitivity);
    }

    public void setTermsIntegrated(TermsIntegrated termsIntegrated, int ord) {
        this.termsIntegrated = termsIntegrated;
        this.ord = ord;
    }
}

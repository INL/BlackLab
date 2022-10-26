package nl.inl.blacklab.codec;

import java.io.IOException;

import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.CompiledAutomaton;

import nl.inl.blacklab.forwardindex.TermsSegmentReader;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * Overridden version of the Lucene Terms class so we
 * can access our BLFieldsProducer from the rest of our code.
 * We need this to access the forward index.
 */
public class BLTerms extends Terms implements TermsSegmentReader {

    /** FieldProducer, so it can be accessed from outside the Codec (for access to forward index) */
    private final BlackLab40PostingsReader fieldsProducer;

    /** The Lucene terms object we're wrapping */
    private final Terms terms;

    /** The global terms object, which we use to implement get() and termsEqual() */
    private nl.inl.blacklab.forwardindex.Terms termsIntegrated;

    /** Our segment number */
    private int ord;

    public BLTerms(Terms terms, BlackLab40PostingsReader fieldsProducer) {
        this.terms = terms;
        this.fieldsProducer = fieldsProducer;
    }

    public BlackLab40PostingsReader getFieldsProducer() {
        return fieldsProducer;
    }

    public BlackLab40StoredFieldsReader getStoredFieldsReader() throws IOException {
        return fieldsProducer.getStoredFieldReader();
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

    @Override
    public String get(int id) {
        return termsIntegrated.get(termsIntegrated.segmentIdToGlobalId(ord, id));
    }

    @Override
    public boolean termsEqual(int[] termIds, MatchSensitivity sensitivity) {
        int[] globalTermIds = termsIntegrated.segmentIdsToGlobalIds(ord, termIds);
        return termsIntegrated.termsEqual(globalTermIds, sensitivity);
    }

    public void setTermsIntegrated(nl.inl.blacklab.forwardindex.Terms termsIntegrated, int ord) {
        this.termsIntegrated = termsIntegrated;
        this.ord = ord;
    }

}

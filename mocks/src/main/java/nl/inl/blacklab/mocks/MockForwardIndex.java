package nl.inl.blacklab.mocks;

import java.util.List;

import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.Collators;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.indexmetadata.Annotation;

public class MockForwardIndex implements AnnotationForwardIndex {

    /** The unique terms in our index */
    private final Terms terms;

    public MockForwardIndex(Terms terms) {
        this.terms = terms;
    }

    @Override
    public void initialize() {
        // Nothing to do
    }

    @Override
    public List<int[]> retrievePartsInt(int fiid, int[] start, int[] end) {
        //
        return null;
    }

    @Override
    public Terms terms() {
        //
        return terms;
    }

    @Override
    public int numDocs() {
        //
        return 0;
    }

    @Override
    public int docLength(int fiid) {
        //
        return 0;
    }

    @Override
    public boolean canDoNfaMatching() {
        return false;
    }

    @Override
    public String toString() {
        return "MockForwardIndex";
    }

    @Override
    public Collators collators() {
        return null;
    }

    @Override
    public Annotation annotation() {
        return null;
    }

}

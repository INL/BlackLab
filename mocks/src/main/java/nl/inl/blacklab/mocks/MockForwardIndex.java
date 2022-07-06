package nl.inl.blacklab.mocks;

import java.util.List;

import nl.inl.blacklab.forwardindex.AnnotationForwardIndexExternalAbstract;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.indexmetadata.Annotation;

public class MockForwardIndex extends AnnotationForwardIndexExternalAbstract {

    /** The unique terms in our index */
    private final Terms terms;

    public MockForwardIndex(Terms terms) {
        super(null, null, null);
        this.terms = terms;
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
    public Annotation annotation() {
        return null;
    }

}

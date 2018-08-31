package nl.inl.blacklab.mocks;

import java.util.List;
import java.util.Set;

import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.FiidLookup;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.indexmetadata.Annotation;

public class MockForwardIndex extends AnnotationForwardIndex {

    private Terms terms;

    public MockForwardIndex(Terms terms) {
        super(null, null, true);
        this.terms = terms;
    }

    @Override
    public void setIdTranslateInfo(FiidLookup fiidLookup, Annotation annotation) {
        //

    }

    @Override
    public int luceneDocIdToFiid(int docId) {
        //
        return 0;
    }

    @Override
    public void close() {
        //

    }

    @Override
    public int addDocument(List<String> content, List<Integer> posIncr) {
        //
        return 0;
    }

    @Override
    public void deleteDocumentByFiid(int fiid) {
        //

    }

    @Override
    public List<int[]> retrievePartsIntByFiid(int fiid, int[] start, int[] end) {
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
    public long freeSpace() {
        //
        return 0;
    }

    @Override
    public int freeBlocks() {
        //
        return 0;
    }

    @Override
    public long totalSize() {
        //
        return 0;
    }

    @Override
    public int docLengthByFiid(int fiid) {
        //
        return 0;
    }

    @Override
    protected void setLargeTermsFileSupport(boolean b) {
        //

    }

    @Override
    public Set<Integer> idSet() {
        return null;
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

package nl.inl.blacklab.mocks;

import java.util.ArrayList;
import java.util.List;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.HitsList;
import nl.inl.blacklab.search.results.QueryInfo;

public class MockHits extends HitsList {

    public MockHits(BlackLabIndex index, AnnotatedField field) {
        super(QueryInfo.create(index, field), null);
    }

    public MockHits(BlackLabIndex index, AnnotatedField field, int[] doc, int[] start, int[] end) {
        super(QueryInfo.create(index, field), createHitList(doc, start, end));
    }

    private static List<Hit> createHitList(int[] doc, int[] start, int[] end) {
        List<Hit> hits = new ArrayList<>();
        for (int i = 0; i < doc.length; i++) {
            hits.add(Hit.create(doc[i], start[i], end[i]));
        }
        return hits;
    }

    @Override
    public String toString() {
        return "MockHits#" + resultsObjId();
    }

}

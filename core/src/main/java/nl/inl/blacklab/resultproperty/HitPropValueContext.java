package nl.inl.blacklab.resultproperty;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.results.Hits;

public abstract class HitPropValueContext extends HitPropValue {

    protected Terms terms;

    protected Annotation annotation;

    public HitPropValueContext(Hits hits, Annotation annotation) {
        this.annotation = annotation;
        this.terms = hits.getSearcher().forwardIndex(annotation).getTerms();
    }
}

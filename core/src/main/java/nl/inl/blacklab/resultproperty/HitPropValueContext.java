package nl.inl.blacklab.resultproperty;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.results.Hits;

public abstract class HitPropValueContext extends HitPropValue {

    protected Terms terms;

    protected Annotation annotation;

    public HitPropValueContext(Hits hits, Annotation annotation) {
        this.annotation = annotation;
        this.terms = hits.index().annotationForwardIndex(annotation).terms();
    }

    public HitPropValueContext(BlackLabIndex index, Annotation annotation) {
        this.annotation = annotation;
        this.terms = index.annotationForwardIndex(annotation).terms();
    }
}

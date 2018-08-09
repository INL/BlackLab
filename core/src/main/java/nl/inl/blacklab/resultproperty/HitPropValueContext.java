package nl.inl.blacklab.resultproperty;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.results.HitsAbstract;

public abstract class HitPropValueContext extends HitPropValue {

    protected Terms terms;

    protected Annotation annotation;

    public HitPropValueContext(HitsAbstract hits, Annotation annotation) {
        this.annotation = annotation;
        this.terms = hits.index().forwardIndex(annotation).terms();
    }
}

package nl.inl.blacklab.resultproperty;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.results.Hits;

public abstract class PropertyValueContext extends PropertyValue {

    protected Terms terms;

    protected Annotation annotation;

    public PropertyValueContext(Hits hits, Annotation annotation) {
        this(hits.index(), annotation);
    }

    public PropertyValueContext(BlackLabIndex index, Annotation annotation) {
        this.annotation = annotation;
        this.terms = index.annotationForwardIndex(annotation).terms();
    }
}

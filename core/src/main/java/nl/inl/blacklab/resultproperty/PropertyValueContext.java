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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((annotation == null) ? 0 : annotation.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PropertyValueContext other = (PropertyValueContext) obj;
        if (annotation == null) {
            if (other.annotation != null)
                return false;
        } else if (!annotation.equals(other.annotation))
            return false;
        return true;
    }
    
}

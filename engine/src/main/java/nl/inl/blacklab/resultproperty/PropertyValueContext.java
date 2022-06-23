package nl.inl.blacklab.resultproperty;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.Annotation;

public abstract class PropertyValueContext extends PropertyValue {

    protected final Terms terms;

    protected final String annotationName;

    public PropertyValueContext(BlackLabIndex index, Annotation annotation) {
        this.annotationName = annotation.name();
        this.terms = index == null ? null : index.annotationForwardIndex(annotation).terms();
    }

    public PropertyValueContext(Terms terms, String annotationName) {
        this.annotationName = annotationName;
        this.terms = terms;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((annotationName == null) ? 0 : annotationName.hashCode());
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
        if (annotationName == null) {
            if (other.annotationName != null)
                return false;
        } else if (!annotationName.equals(other.annotationName))
            return false;
        return true;
    }

}

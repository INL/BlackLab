package nl.inl.blacklab.search.indexmetadata;

public class AnnotationSensitivityImpl implements AnnotationSensitivity {
    
    Annotation annotation;
    
    MatchSensitivity sensitivity;

    @Override
    public Annotation annotation() {
        return annotation;
    }

    @Override
    public MatchSensitivity sensitivity() {
        return sensitivity;
    }

    AnnotationSensitivityImpl(Annotation annotation, MatchSensitivity sensitivity) {
        super();
        this.annotation = annotation;
        this.sensitivity = sensitivity;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((annotation == null) ? 0 : annotation.hashCode());
        result = prime * result + ((sensitivity == null) ? 0 : sensitivity.hashCode());
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
        AnnotationSensitivityImpl other = (AnnotationSensitivityImpl) obj;
        if (annotation == null) {
            if (other.annotation != null)
                return false;
        } else if (!annotation.equals(other.annotation))
            return false;
        if (sensitivity == null) {
            if (other.sensitivity != null)
                return false;
        } else if (!sensitivity.equals(other.sensitivity))
            return false;
        return true;
    }
    
    @Override
    public String toString() {
        return luceneField();
    }

    
}

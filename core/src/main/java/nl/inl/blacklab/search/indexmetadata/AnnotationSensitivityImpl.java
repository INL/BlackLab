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

}

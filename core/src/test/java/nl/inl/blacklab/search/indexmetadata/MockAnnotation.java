package nl.inl.blacklab.search.indexmetadata;

import java.util.Collection;

public class MockAnnotation implements Annotation {
    
    private AnnotatedField field;

    private String name;
    
    public MockAnnotation(String name) {
        this.name = name;
    }
    
    public void setField(AnnotatedField field) {
        this.field = field;
    }

    @Override
    public AnnotatedField field() {
        return field;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String uiType() {
        return null;
    }

    @Override
    public String description() {
        return null;
    }

    @Override
    public boolean hasForwardIndex() {
        return false;
    }

    @Override
    public AnnotationSensitivity offsetsSensitivity() {
        return null;
    }

    @Override
    public String sensitivitySettingDesc() {
        return null;
    }

    @Override
    public Collection<AnnotationSensitivity> sensitivities() {
        return null;
    }

    @Override
    public boolean hasSensitivity(MatchSensitivity sensitivity) {
        return false;
    }

    @Override
    public AnnotationSensitivity sensitivity(MatchSensitivity sensitivity) {
        return null;
    }

    @Override
    public String displayName() {
        return null;
    }

    @Override
    public boolean isInternal() {
        return false;
    }

    @Override
    public Annotation subannotation(String subName) {
        return null;
    }

    @Override
    public String subName() {
        return null;
    }

    @Override
    public boolean isSubannotation() {
        return false;
    }
    
}
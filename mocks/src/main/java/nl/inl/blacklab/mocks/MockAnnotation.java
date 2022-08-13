package nl.inl.blacklab.mocks;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.CustomProps;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

public class MockAnnotation implements Annotation {
    
    private final IndexMetadata indexMetadata;
    
    private AnnotatedField field;

    private final String name;
    
    public MockAnnotation(String name) {
        this(null, name);
    }
    
    public MockAnnotation(IndexMetadata indexMetadata, String name) {
        this.indexMetadata = indexMetadata;
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
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((field == null) ? 0 : field.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
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
        MockAnnotation other = (MockAnnotation) obj;
        if (field == null) {
            if (other.field != null)
                return false;
        } else if (!field.equals(other.field))
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        return true;
    }

    @Override
    public Set<String> subannotationNames() {
        return Collections.emptySet();
    }

    @Override
    public void setSubAnnotation(Annotation parentAnnotation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CustomProps custom() {
        return CustomProps.NONE;
    }
}

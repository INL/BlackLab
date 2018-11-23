package nl.inl.blacklab.search.indexmetadata;

import java.util.Collection;
import java.util.Set;

/**
 * Subannotation descriptor.
 * 
 * Really only differ in the return value of subName() and isSubannotation();
 */
final class Subannotation implements Annotation {
    
    private final IndexMetadata indexMetadata;

    private final AnnotationImpl mainAnnotation;
    
    private final String subName;

    Subannotation(IndexMetadata indexMetadata, AnnotationImpl annotationImpl, String subName) {
        this.indexMetadata = indexMetadata;
        mainAnnotation = annotationImpl;
        this.subName = subName;
    }
    
    @Override
    public IndexMetadata indexMetadata() {
        return indexMetadata;
    }
    
    @Override
    public Annotation parentAnnotation() {
        return mainAnnotation;
    }

    @Override
    public AnnotatedField field() {
        return mainAnnotation.field();
    }

    @Override
    public String name() {
        return mainAnnotation.name();
    }

    @Override
    public String uiType() {
        return mainAnnotation.uiType();
    }

    @Override
    public String description() {
        return mainAnnotation.description() + " (subannotation: " + subName + ")";
    }

    @Override
    public boolean hasForwardIndex() {
        return mainAnnotation.hasForwardIndex();
    }

    @Override
    public AnnotationSensitivity offsetsSensitivity() {
        return mainAnnotation.offsetsSensitivity();
    }

    @Override
    public Collection<AnnotationSensitivity> sensitivities() {
        return mainAnnotation.sensitivities();
    }

    @Override
    public boolean hasSensitivity(MatchSensitivity sensitivity) {
        return mainAnnotation.hasSensitivity(sensitivity);
    }

    @Override
    public AnnotationSensitivity sensitivity(MatchSensitivity sensitivity) {
        return mainAnnotation.sensitivity(sensitivity);
    }

    @Override
    public String displayName() {
        return mainAnnotation.displayName() + "/" + subName;
    }

    @Override
    public boolean isInternal() {
        return mainAnnotation.isInternal();
    }

    @Override
    public Annotation subannotation(String subName) {
        throw new UnsupportedOperationException("Cannot create subsubannotation");
    }

    @Override
    public Set<String> subannotationNames() {
        throw new UnsupportedOperationException("Subsubannotations don't exist");
    }

    @Override
    public String subName() {
        return subName;
    }

    @Override
    public boolean isSubannotation() {
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((mainAnnotation == null) ? 0 : mainAnnotation.hashCode());
        result = prime * result + ((subName == null) ? 0 : subName.hashCode());
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
        Subannotation other = (Subannotation) obj;
        if (mainAnnotation == null) {
            if (other.mainAnnotation != null)
                return false;
        } else if (!mainAnnotation.equals(other.mainAnnotation))
            return false;
        if (subName == null) {
            if (other.subName != null)
                return false;
        } else if (!subName.equals(other.subName))
            return false;
        return true;
    }

    @Override
    public void setSubAnnotation(Annotation parentAnnotation) {
        throw new UnsupportedOperationException("Can only call this for new-style indexes");
    }
}
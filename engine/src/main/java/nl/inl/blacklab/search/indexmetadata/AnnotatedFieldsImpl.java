package nl.inl.blacklab.search.indexmetadata;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

import com.fasterxml.jackson.annotation.JsonProperty;

@XmlAccessorType(XmlAccessType.FIELD)
final class AnnotatedFieldsImpl implements AnnotatedFields {

    /** The annotated fields in our index */
    @JsonProperty("fields")
    private final Map<String, AnnotatedFieldImpl> annotatedFields;
    
    /**
     * The main contents field in our index. This is either the annotated field with
     * the name "contents", or if that doesn't exist, the first annotated field found.
     */
    @XmlTransient
    private AnnotatedFieldImpl mainAnnotatedField;

    /**
     * The main contents field in our index. This is either the annotated field with
     * the name "contents", or if that doesn't exist, the first annotated field found.
     */
    @JsonProperty("mainAnnotatedField")
    private String mainAnnotatedFieldName;

    /**
     * Logical groups of annotations, for presenting them in the user interface.
     */
    @XmlTransient
    private final Map<String, AnnotationGroups> annotationGroupsPerField = new LinkedHashMap<>();

    public AnnotatedFieldsImpl() {
        annotatedFields = new TreeMap<>();
    }

    @Override
    public AnnotatedField main() {
        return mainAnnotatedField;
    }

    @Override
    public Iterator<AnnotatedField> iterator() {
        Iterator<AnnotatedFieldImpl> it = annotatedFields.values().iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public AnnotatedField next() {
                return it.next();
            }
        };
    }

    @Override
    public Stream<AnnotatedField> stream() {
        return annotatedFields.values().stream().map(f -> f);
    }

    @Override
    public AnnotatedFieldImpl get(String fieldName) {
        if (!annotatedFields.containsKey(fieldName))
            throw new IllegalArgumentException("Annotated field '" + fieldName + "' not found!");
        return annotatedFields.get(fieldName);
    }

    @Override
    public boolean exists(String fieldName) {
        return annotatedFields.containsKey(fieldName);
    }

    public void freeze() {
        annotatedFields.values().forEach(AnnotatedFieldImpl::freeze);
    }

    public void put(String fieldName, AnnotatedFieldImpl fieldDesc) {
        annotatedFields.put(fieldName, fieldDesc);
    }

    public void setMainAnnotatedField(AnnotatedFieldImpl mainAnnotatedField) {
        this.mainAnnotatedField = mainAnnotatedField;
        this.mainAnnotatedFieldName = mainAnnotatedField.name();
    }

    public void clearAnnotationGroups() {
        annotationGroupsPerField.clear();
    }

    public void putAnnotationGroups(String fieldName, AnnotationGroups annotationGroups) {
        annotationGroupsPerField.put(fieldName, annotationGroups);
    }
    
    @Override
    public AnnotationGroups annotationGroups(String fieldName) {
        return annotationGroupsPerField.get(fieldName);
    }
}

package nl.inl.blacklab.search.indexmetadata;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

import com.fasterxml.jackson.annotation.JsonProperty;

import nl.inl.blacklab.search.BlackLabIndex;

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
        if ( (mainAnnotatedField == null && mainAnnotatedFieldName == null) || fieldName.equals("contents")) {
            mainAnnotatedFieldName = fieldName;
            mainAnnotatedField = fieldDesc;
        }
    }

    public void setMainAnnotatedField(AnnotatedFieldImpl mainAnnotatedField) {
        this.mainAnnotatedField = mainAnnotatedField;
        this.mainAnnotatedFieldName = mainAnnotatedField == null ? null : mainAnnotatedField.name();
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

    public void fixAfterDeserialization(BlackLabIndex index, IndexMetadataIntegrated metadata) {
        setMainAnnotatedField(annotatedFields.get(mainAnnotatedFieldName));

        CustomProps custom = metadata.custom();
        if (custom.containsKey("annotationGroups")) {
            clearAnnotationGroups();
            Map<String, List<Map<String, Object>>> groupingsPerField =custom.get("annotationGroups", Collections.emptyMap());
            for (Map.Entry<String, List<Map<String, Object>>> entry: groupingsPerField.entrySet()) {
                String fieldName = entry.getKey();
                List<Map<String, Object>> groups = entry.getValue();
                List<AnnotationGroup> annotationGroups = IntegratedMetadataUtil.extractAnnotationGroups(this, fieldName, groups);
                putAnnotationGroups(fieldName, new AnnotationGroups(fieldName, annotationGroups));
            }
        }

        for (Map.Entry<String, AnnotatedFieldImpl> e: annotatedFields.entrySet()) {
            e.getValue().fixAfterDeserialization(index, e.getKey());
        }

    }
}

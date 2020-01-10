package nl.inl.blacklab.search.indexmetadata;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

final class AnnotatedFieldsImpl implements AnnotatedFields {
    /** The annotated fields in our index */
    private Map<String, AnnotatedFieldImpl> annotatedFields;
    
    /**
     * The main contents field in our index. This is either the annotated field with
     * the name "contents", or if that doesn't exist, the first annotated field found.
     */
    private AnnotatedFieldImpl mainContentsField;

    /**
     * Logical groups of annotations, for presenting them in the user interface.
     */
    private Map<String, AnnotationGroups> annotationGroupsPerField = new LinkedHashMap<>();
    
    public AnnotatedFieldsImpl() {
        annotatedFields = new TreeMap<>();
    }

    @Override
    public AnnotatedField main() {
        return mainContentsField;
    }

    @Override
    public Iterator<AnnotatedField> iterator() {
        Iterator<AnnotatedFieldImpl> it = annotatedFields.values().iterator();
        return new Iterator<AnnotatedField>() {
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
        return annotatedFields.values().stream().map(f -> (AnnotatedField)f);
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
        annotatedFields.values().forEach(f -> f.freeze());
    }

    public void put(String fieldName, AnnotatedFieldImpl fieldDesc) {
        annotatedFields.put(fieldName, fieldDesc);
    }

    public void setMainContentsField(AnnotatedFieldImpl mainContentsField) {
        this.mainContentsField = mainContentsField;
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
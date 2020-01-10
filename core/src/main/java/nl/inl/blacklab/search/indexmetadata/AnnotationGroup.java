package nl.inl.blacklab.search.indexmetadata;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Logical grouping of annotations */
public class AnnotationGroup implements Iterable<Annotation> {
    
    private AnnotatedFieldsImpl annotatedFields;
    
    private String fieldName;
    
    private String groupName;
    
    private List<String> annotations;
    
    private boolean addRemainingAnnotations;

    public AnnotationGroup(AnnotatedFieldsImpl annotatedFields, String fieldName, String groupName, List<String> annotations,
            boolean addRemainingAnnotations) {
        this.annotatedFields = annotatedFields;
        this.fieldName = fieldName;
        this.groupName = groupName;
        this.annotations = new ArrayList<>(annotations);
        this.addRemainingAnnotations = addRemainingAnnotations; 
    }

    public String fieldName() {
        return fieldName;
    }

    public String groupName() {
        return groupName;
    }

    public List<Annotation> annotations() {
        AnnotatedFieldImpl f = annotatedFields.get(fieldName);
        return annotations.stream().map(name -> f.annotation(name)).filter(a -> a != null).collect(Collectors.toList());
    }

    public boolean addRemainingAnnotations() {
        return addRemainingAnnotations;
    }
    
    @Override
    public Iterator<Annotation> iterator() {
        AnnotatedFieldImpl f = annotatedFields.get(fieldName);
        return annotations.stream().map(name -> f.annotation(name)).iterator();
    }

    public Stream<Annotation> stream() {
        AnnotatedFieldImpl f = annotatedFields.get(fieldName);
        return annotations.stream().map(name -> f.annotation(name));
    }

}

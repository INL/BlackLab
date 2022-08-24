package nl.inl.blacklab.search.indexmetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

/** Logical grouping of annotations */
@XmlAccessorType(XmlAccessType.FIELD)
public class AnnotationGroup implements Iterable<String> {
    
    private final String fieldName;
    
    private final String groupName;
    
    private final List<String> annotations;
    
    private final boolean addRemainingAnnotations;

    public AnnotationGroup(String fieldName, String groupName, List<String> annotations,
            boolean addRemainingAnnotations) {
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

    public List<String> annotations() {
        return Collections.unmodifiableList(annotations);
    }

    public boolean addRemainingAnnotations() {
        return addRemainingAnnotations;
    }
    
    @Override
    public Iterator<String> iterator() {
        return annotations.iterator();
    }

    public Stream<String> stream() {
        return annotations.stream();
    }

}

package nl.inl.blacklab.search.indexmetadata;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/** Groups of annotations for a single field */
public class AnnotationGroups implements Iterable<AnnotationGroup> {
    
    private String fieldName;
    
    private List<AnnotationGroup> groups;
    
    public AnnotationGroups(String fieldName, List<AnnotationGroup> groups) {
        this.fieldName = fieldName;
        this.groups = new ArrayList<>(groups);
    }

    public String fieldName() {
        return fieldName;
    }

    public List<AnnotationGroup> groups() {
        return groups;
    }
    
    @Override
    public Iterator<AnnotationGroup> iterator() {
        return groups.iterator();
    }

    public Stream<AnnotationGroup> stream() {
        return groups.stream();
    }

    public AnnotationGroup get(String name) {
        return groups.stream().filter(g -> g.groupName().equals(name)).findFirst().orElse(null);
    }
    
}

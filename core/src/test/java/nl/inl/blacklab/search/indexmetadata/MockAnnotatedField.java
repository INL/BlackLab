package nl.inl.blacklab.search.indexmetadata;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

public class MockAnnotatedField implements AnnotatedField {
    
    String name;
    
    private List<Annotation> annotations;
    
    public MockAnnotatedField(String name, List<Annotation> annotations) {
        this.name = name;
        this.annotations = annotations;
        for (Annotation a: annotations) {
            if (a instanceof MockAnnotation) {
                ((MockAnnotation) a).setField(this);
            }
        }
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public String displayName() {
        return null;
    }

    @Override
    public String description() {
        return null;
    }

    @Override
    public boolean hasContentStore() {
        return false;
    }

    @Override
    public Annotations annotations() {
        return new Annotations() {

            @Override
            public Annotation main() {
                return annotations.get(0);
            }

            @Override
            public Iterator<Annotation> iterator() {
                return annotations.iterator();
            }

            @Override
            public Stream<Annotation> stream() {
                return annotations.stream();
            }

            @Override
            public boolean exists(String name) {
                return annotations.stream().anyMatch(a -> a.name().equals(name));
            }

            @Override
            public Annotation get(String name) {
                return annotations.stream().filter(a -> a.name().equals(name)).findFirst().orElse(null);
            }
            
        };
    }

    @Override
    public boolean hasLengthTokens() {
        return false;
    }

    @Override
    public boolean hasXmlTags() {
        return false;
    }

    @Override
    public boolean hasPunctuationForwardIndex() {
        return false;
    }

    @Override
    public String tokenLengthField() {
        return null;
    }
    
}
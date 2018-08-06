package nl.inl.blacklab.search.indexmetadata;

import java.util.Iterator;
import java.util.stream.Stream;

/** Annotations on an annotated field. */
public interface Annotations extends Iterable<Annotation> {

    /**
     * The main annotation.
     * 
     * @return the main annotation
     */
    Annotation main();

    /**
     * Iterate over the annotations.
     * 
     * @return iterator
     */
    @Override
    Iterator<Annotation> iterator();

    /**
     * Stream the annotations.
     * 
     * @return stream
     */
    Stream<Annotation> stream();
    
    /**
     * Does the specified annotation exist?
     * 
     * @param name annotation name
     * @return true if it exists, false if not
     */
    boolean exists(String name);

    /**
     * Get the description of one complex field.
     * 
     * @param name name of the annotation
     * @return the annotation, or null if it doesn't exist
     */
    Annotation get(String name);

}

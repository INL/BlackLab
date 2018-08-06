package nl.inl.blacklab.search.indexmetadata.nint;

import java.util.Iterator;
import java.util.stream.Stream;

/** Annotated fields on a BlackLab index */
public interface AnnotatedFields extends Iterable<AnnotatedField> {

    /**
     * The main contents field in our index. This is either the complex field with
     * the name "contents", or if that doesn't exist, the first complex field found.
     * 
     * @return the main contents field
     */
    AnnotatedField main();

    /**
     * Iterate over the annotated fields in our index.
     * 
     * @return iterator
     */
    @Override
    Iterator<AnnotatedField> iterator();

    /**
     * Stream the annotated fields in our index.
     * 
     * @return stream
     */
    Stream<AnnotatedField> stream();

    /**
     * Get the description of one complex field
     * 
     * @param fieldName name of the field
     * @return the field description, or null if it doesn't exist
     */
    AnnotatedField field(String fieldName);

    /**
     * Does the specified field exist?
     * 
     * @param fieldName field name
     * @return true if it exists, false if not
     */
    boolean exists(String fieldName);

}

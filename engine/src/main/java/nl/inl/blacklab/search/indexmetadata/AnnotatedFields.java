package nl.inl.blacklab.search.indexmetadata;

import java.util.Iterator;
import java.util.stream.Stream;

import nl.inl.blacklab.indexers.config.ConfigAnnotatedField;

/** Annotated fields on a BlackLab index */
public interface AnnotatedFields extends Iterable<AnnotatedField> {

    /**
     * The main contents field in our index. This is either the annotated field with
     * the name "contents", or if that doesn't exist, the first annotated field found.
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
     * Get the description of one annotated field
     * 
     * @param fieldName name of the field
     * @return the field description, or null if it doesn't exist
     */
    AnnotatedField get(String fieldName);

    /**
     * Get annotated field by its full name, or by its version name (parallel corpus).
     *
     * If it's not a full annotated field name, it's assumed to be a version name in a
     * parallel corpus.
     * The main annotated field is used and the version is replaced with the one supplied.
     * Example: version "nl" of main annotated field "contents__en" is "contents__nl".
     *
     * @param name field name or version name
     * @return the annotated field, or null if it doesn't exist
     */
    default AnnotatedField getByFieldOrVersionName(String name) {
        // Was a field name supplied?
        if (!exists(name)) {
            // No; see if it's a version (e.g. different language in parallel corpus) of the main annotated field
            name = AnnotatedFieldNameUtil.changeParallelFieldVersion(main().name(), name);
        }
        return get(name);
    }

    /**
     * Does the specified field exist?
     * 
     * @param fieldName field name
     * @return true if it exists, false if not
     */
    boolean exists(String fieldName);

    AnnotationGroups annotationGroups(String fieldName);

    void addFromConfig(ConfigAnnotatedField config);
}

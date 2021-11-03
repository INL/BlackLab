package nl.inl.blacklab.search.indexmetadata;

import java.util.List;
import java.util.stream.Stream;

/** Metadata fields in an index. */
public interface MetadataFields extends Iterable<MetadataField> {
	
    /** Name of special field type for persistent identifier */
    String PID = "pid";

    /** Name of special field type for document title */
    String TITLE = "title";

    /** Name of special field type for document author */
    String AUTHOR = "author";

    /** Name of special field type for document date */
    String DATE = "date";

	/**
	 * Name of the default analyzer to use for metadata fields.
	 * @return the analyzer name (or DEFAULT for the BlackLab default)
	 */
	String defaultAnalyzerName();

	Stream<MetadataField> stream();

	MetadataField get(String fieldName);
	
	MetadataFieldGroups groups();
	
	/**
	 * Returns the one of the special fields, if configured.
	 *
	 * These fields can be configured in the indexmetadata.json file.
	 * If it wasn't specified there, an intelligent guess is used.
	 * 
	 * Currently, supported types are pid, author, title and date.
	 * But we might want to allow user-definable special fields in
	 * the future (to help unify metadata across different indices),
	 * so we've kept the special field type a string instead of e.g.
	 * an enum.
	 * 
	 * @param specialFieldType type of field 
	 * @return name of the pid field, or null if none found
	 */
	MetadataField special(String specialFieldType);

	/**
	 * Does the specified field exist?
	 * 
	 * @param name
	 * @return true if it exists, false if not
	 */
    boolean exists(String name);

    List<String> names();
	
}

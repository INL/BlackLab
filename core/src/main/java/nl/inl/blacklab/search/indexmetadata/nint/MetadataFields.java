package nl.inl.blacklab.search.indexmetadata.nint;

import java.util.Collection;
import java.util.List;

/** Metadata fields in an index. */
public interface MetadataFields {
	
	/**
	 * Name of the default analyzer to use for metadata fields.
	 * @return the analyzer name (or DEFAULT for the BlackLab default)
	 */
	String defaultAnalyzerName();

	/** Get the names of all the metadata fields in our index
	 * @return the names */
	Collection<MetadataField> fields();

	MetadataField field(String fieldName);
	
	/** A named group of ordered metadata fields */
	interface Group {

	    String name();

        List<String> fields();

        boolean addRemainingFields();
	}

	Collection<MetadataFields.Group> fieldGroups();

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
	MetadataField specialField(String specialFieldType);
	
}

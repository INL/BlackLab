package nl.inl.blacklab.search.indexmetadata.nint;

import java.util.Collection;

/** Annotated fields on a BlackLab index */
public interface AnnotatedFields {
	
	/**
	 * The main contents field in our index. This is either the complex field with the name "contents",
	 * or if that doesn't exist, the first complex field found.
	 * @return the main contents field
	 */
	AnnotatedField mainField();

	/** Get the names of all the complex fields in our index
	 * @return the complex field names */
	Collection<AnnotatedField> fields();

	/** Get the description of one complex field
	 * @param fieldName name of the field
	 * @return the field description, or null if it doesn't exist */
	AnnotatedField field(String fieldName);
	
	/**
	 * While indexing, check if a complex field is already registered in the
	 * metadata, and if not, add it now.
	 *
	 * @param fieldName field name
	 * @param mainPropName main property name
	 */
	void registerField(String fieldName, String mainPropName);
	
}
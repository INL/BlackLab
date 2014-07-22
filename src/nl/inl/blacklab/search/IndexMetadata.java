package nl.inl.blacklab.search;

import java.io.File;

/**
 * Reads/writes the indexmetadata.json file, and provides
 * an interface to the information contained within it.
 */
public class IndexMetadata {

	/**
	 * Metadata field config.
	 */
	public class MetaFieldConfig {

	}

	/**
	 * Complex field config.
	 */
	public class ComplexFieldConfig {

	}

	/**
	 * What to index and how to index it.
	 */
	public class IndexerConfig {

	}

	/**
	 * Construct an index metadata object with default values.
	 */
	public IndexMetadata() {

	}

	/**
	 * Construct an index metadata object from a JSON file.
	 * @param metadataFile the file to read
	 */
	public IndexMetadata(File metadataFile) {

	}

	/**
	 * Write the index metadata to a JSON file.
	 * @param metadataFile the file to write
	 */
	public void write(File metadataFile) {

	}

	/**
	 * Get the configuration for a metadata field
	 * @param fieldName field name
	 * @return the configuration
	 */
	public MetaFieldConfig getMetaFieldConfig(String fieldName) {
		return null; // FIXME
	}

	/**
	 * Get the configuration for a complex field
	 * @param fieldName field name
	 * @return the configuration
	 */
	public ComplexFieldConfig getComplexFieldConfig(String fieldName) {
		return null; // FIXME
	}

	/**
	 * Get the configuration for the indexer
	 * @return the configuration
	 */
	public IndexerConfig getIndexerConfig() {
		return null; // FIXME
	}


}

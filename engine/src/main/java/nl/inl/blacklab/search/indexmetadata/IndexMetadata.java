package nl.inl.blacklab.search.indexmetadata;

import java.io.File;

import nl.inl.blacklab.indexers.config.TextDirection;

/** Information about a BlackLab index, including its fields structure. */
public interface IndexMetadata extends Freezable<IndexMetadata> {

    static String indexNameFromDirectory(File directory) {
        String name = directory.getName();
        if (name.equals("index"))
            name = directory.getAbsoluteFile().getParentFile().getName();
        return name;
    }

    AnnotatedFields annotatedFields();
	
	default AnnotatedField mainAnnotatedField() {
	    return annotatedFields().main();
	}
	
    default AnnotatedField annotatedField(String name) {
        return annotatedFields().get(name);
    }
    
	MetadataFields metadataFields();
	
	default MetadataField metadataField(String name) {
	    return metadataFields().get(name);
	}

	/**
	 * Get the display name for the index.
	 *
	 * If no display name was specified, returns the name of the index directory.
	 *
	 * @return the display name
	 */
	String displayName();

	/**
	 * Get a description of the index, if specified
	 * @return the description
	 */
	String description();

	/**
	 * Is the content freely viewable by all users, or is it restricted?
	 * @return true if the full content may be retrieved by anyone
	 */
	boolean contentViewable();

    /**
     * What's the text direction of this corpus?
     * @return text direction
     */
	TextDirection textDirection();

	/**
	 * What format(s) is/are the documents in?
	 *
	 * This is in the form of a format identifier as understood
	 * by the DocumentFormats class (either an abbreviation or a
	 * (qualified) class name).
	 *
	 * @return the document format(s)
	 */
	String documentFormat();

	/**
	 * What version of the index format is this?
	 * @return the index format version
	 */
	String indexFormat();

	/**
	 * When was this index created?
	 * @return date/time
	 */
	String timeCreated();

	/**
	 * When was this index last modified?
	 * @return date/time
	 */
	String timeModified();

	/**
	 * When was the BlackLab.jar used for indexing built?
	 * @return date/time
	 */
	String indexBlackLabBuildTime();

	/**
	 * When was the BlackLab.jar used for indexing built?
	 * @return date/time stamp
	 */
	String indexBlackLabVersion();

	/**
	 * How many tokens are in the main annotated field?
     *
	 * @return number of tokens
	 */
	long tokenCount();

    /**
     * How many documents are in the index?
     *
     * This reports the number of live documents in the index
     * that have a value for the main annotated field.
     *
     * This does therefore not include the index metadata document
     * (if using integrated index format).
     *
     * @return number of documents
     */
    int documentCount();

	/**
	 * Is this a new, empty index?
	 *
	 * An empty index is one that doesn't have a main contents field yet,
	 * or has a main contents field but no indexed tokens yet.
	 *
	 * @return true if it is, false if not.
	 */
	boolean isNewIndex();

    /**
     * Return the id of the index metadata document.
     *
     * This document, if it exists, should be skipped when searching.
     *
     * @return special docId, or -1 if none
     */
    default int metadataDocId() {
        return -1;
    }

}

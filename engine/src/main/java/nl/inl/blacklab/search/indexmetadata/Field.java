package nl.inl.blacklab.search.indexmetadata;

/** Shared base interface between metadata and annotated fields */
public interface Field {
	
	/** Get this field's name
	 * @return this field's name */
	String name();

	/** Get this field's display name
	 * @return this field's display name */
	String displayName();

	/** Get this field's display name
	 * @return this field's display name */
	String description();
	
	/** Is this field's content stored in a content store? 
	 * @return true if it does, false if not */
    boolean hasContentStore();

    /** Get the Lucene field that contains character offsets (if any) 
     * @return lucene field containing offsets, or null if there is none */
    String offsetsField();

    /**
     * Get the Lucene field that contains the content id (if this has a content store)
     * @return lucene field containing content id (cid)
     */
    default String contentIdField() {
        return AnnotatedFieldNameUtil.contentIdField(name());
    }

    /**
     * Get the Lucene field that contains our main contents.
     * 
     * This is either the field itself (for metadata) or the main sensitivity of the 
     * main property.
     * 
     * @return Lucene field containing contents
     */
    String contentsFieldName();

}

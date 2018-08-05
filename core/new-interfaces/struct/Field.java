package nl.inl.blacklab.interfaces.struct;

/** Shared base interface between metadata and annotated fields */
public interface Field {
	
	/** Get this complex field's name
	 * @return this field's name */
	public String name();

	/** Get this complex field's display name
	 * @return this field's display name */
	public String displayName();

	/** Get this complex field's display name
	 * @return this field's display name */
	public String description();
	
	/** Is this field's content stored in a content store? 
	 * @return true if it does, false if not */
    public boolean hasContentStore();

}

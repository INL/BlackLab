package nl.inl.blacklab.search.indexstructure;

import nl.inl.util.StringUtil;

public abstract class BaseFieldDesc {
	/** Complex field's name */
	protected String fieldName;

	/** Complex field's name */
	protected String displayName;

	/** Complex field's name */
	protected String description = "";

	public BaseFieldDesc(String fieldName) {
		this(fieldName, null);
	}

	public BaseFieldDesc(String fieldName, String displayName) {
		this.fieldName = fieldName;
		if (displayName == null)
			this.displayName = StringUtil.camelCaseToDisplayable(fieldName, true);
		else
			this.displayName = displayName;
	}

	/** Get this complex field's name
	 * @return this field's name */
	public String getName() {
		return fieldName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	/** Get this complex field's display name
	 * @return this field's display name */
	public String getDisplayName() {
		return displayName;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	/** Get this complex field's display name
	 * @return this field's display name */
	public String getDescription() {
		return description;
	}

}
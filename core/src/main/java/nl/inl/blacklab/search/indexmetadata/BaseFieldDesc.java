package nl.inl.blacklab.search.indexmetadata;

import nl.inl.blacklab.interfaces.struct.Field;
import nl.inl.util.StringUtil;

public abstract class BaseFieldDesc implements Field {
    /** Complex field's name */
    protected String fieldName;

    /** Complex field's name */
    protected String displayName;

    /** Complex field's name */
    protected String description = "";

    /** Does the field have an associated content store? */
    protected boolean contentStore;

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

    /**
     * Get this complex field's name
     * 
     * @return this field's name
     */
    public String name() {
        return fieldName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Get this complex field's display name
     * 
     * @return this field's display name
     */
    public String displayName() {
        return displayName;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Get this complex field's display name
     * 
     * @return this field's display name
     */
    public String description() {
        return description;
    }

    public boolean hasContentStore() {
        return contentStore;
    }

}

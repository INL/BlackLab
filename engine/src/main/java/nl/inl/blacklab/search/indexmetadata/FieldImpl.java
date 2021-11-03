package nl.inl.blacklab.search.indexmetadata;

import org.apache.commons.lang3.StringUtils;

import nl.inl.util.StringUtil;

public abstract class FieldImpl implements Field {
    /** Field's name */
    protected String fieldName;

    /** Field's name */
    protected String displayName;

    /** Field's name */
    protected String description = "";

    /** Does the field have an associated content store? */
    protected boolean contentStore;

    FieldImpl(String fieldName) {
        this(fieldName, null);
    }

    FieldImpl(String fieldName, String displayName) {
        this.fieldName = fieldName;
        this.displayName = StringUtils.defaultIfEmpty(displayName, "");
    }

    /**
     * Get this field's name
     * 
     * @return this field's name
     */
    @Override
    public String name() {
        return fieldName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Get this field's display name
     * 
     * @return this field's display name
     */
    @Override
    public String displayName() {
        if (StringUtils.isEmpty(displayName)) {
            displayName = StringUtil.camelCaseToDisplayable(fieldName, true);
        }
        return displayName;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Get this field's description
     * 
     * @return this field's description
     */
    @Override
    public String description() {
        return description;
    }

    @Override
    public boolean hasContentStore() {
        return contentStore;
    }

    @Override
    public int hashCode() {
        return fieldName.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        FieldImpl other = (FieldImpl) obj;
        return fieldName.equals(other.fieldName);
    }
    
    @Override
    public String toString() {
        return fieldName;
    }

}

package nl.inl.blacklab.search.indexmetadata;

import org.apache.commons.lang3.StringUtils;

import nl.inl.util.StringUtil;

public abstract class FieldImpl implements Field {
    /** Field's name */
    protected final String fieldName;

    /** Does the field have an associated content store? */
    protected boolean contentStore;

    /** Custom field properties */
    protected CustomPropsMap custom = new CustomPropsMap();

    FieldImpl(String fieldName) {
        this.fieldName = fieldName;
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

    /**
     * @deprecated use {@link #custom()} and .put("displayName", "...") instead
     */
    @Deprecated
    public void setDisplayName(String displayName) {
        custom.put("displayName", displayName);
    }

    /**
     * @deprecated use {@link #custom()} and .get("displayName", "") instead
     */
    @Deprecated
    @Override
    public String displayName() {
        String displayName = custom.get("displayName", "");
        if (StringUtils.isEmpty(displayName)) {
            displayName = StringUtil.camelCaseToDisplayable(fieldName, true);
        }
        return displayName;
    }

    /**
     * @deprecated use {@link #custom()} and .put("description", "...") instead
     */
    @Deprecated
    public void setDescription(String description) {
        custom.put("description", description);
    }

    /**
     * @deprecated use {@link #custom()} and .get("description", "") instead
     */
    @Deprecated
    @Override
    public String description() {
        return custom.get("description", "");
    }

    @Override
    public CustomPropsMap custom() {
        return custom;
    }

    public void setContentStore(boolean contentStore) {
        this.contentStore = contentStore;
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

package nl.inl.blacklab.search.indexmetadata;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import nl.inl.util.StringUtil;

public abstract class FieldImpl implements Field {
    /** Field's name */
    protected final String fieldName;

    /** Field's name */
    protected String displayName;

    /** Field's name */
    protected String description = "";

    /** Does the field have an associated content store? */
    protected boolean contentStore;

    /** Custom field properties */
    protected CustomPropsDelegateField custom = new CustomPropsDelegateField();

    FieldImpl(String fieldName) {
        this.fieldName = fieldName;
        this.displayName = "";
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
     * @deprecated use {@link #custom()} and .get("displayName", "") instead
     */
    @Deprecated
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
     * @deprecated use {@link #custom()} and .get("description", "") instead
     */
    @Deprecated
    @Override
    public String description() {
        return description;
    }

    @Override
    public CustomProps custom() {
        return custom;
    }

    public void setCustomProps(CustomProps fromJson) {
        custom.set(fromJson);
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

    /**
     * CustomProps implementation that delegates to the old methods
     */
    public class CustomPropsDelegateField implements CustomProps {

        @Override
        public Object get(String key) {
            MetadataField mf = FieldImpl.this instanceof MetadataField ? (MetadataField) FieldImpl.this : null;
            AnnotatedFieldImpl af = FieldImpl.this instanceof AnnotatedFieldImpl ? (AnnotatedFieldImpl) FieldImpl.this : null;
            switch (key) {
            case "displayName":
                return displayName();
            case "description":
                return description();
            case "uiType":
                return mf == null ? null : mf.uiType();
            case "unknownValue":
                return mf == null ? null : mf.unknownValue();
            case "unknownCondition":
                return mf == null ? null : mf.unknownCondition().toString();
            case "displayValues":
                return mf == null ? null : mf.displayValues();
            case "displayOrder":
                return mf == null ? af.getDisplayOrder() : mf.displayOrder(); // annotations / values ordering
            default:
                return null;
            }
        }

        public void put(String key, Object value) {
            MetadataFieldImpl mf = FieldImpl.this instanceof MetadataFieldImpl ? (MetadataFieldImpl) FieldImpl.this : null;
            AnnotatedFieldImpl af = FieldImpl.this instanceof AnnotatedFieldImpl ? (AnnotatedFieldImpl) FieldImpl.this : null;
            switch (key) {
            case "displayName":
                setDisplayName((String) value);
                break;
            case "description":
                setDescription((String) value);
                break;
            case "uiType":
                if (mf != null) mf.setUiType((String) value);
                break;
            case "unknownValue":
                if (mf != null) mf.setUnknownValue((String) value);
                break;
            case "unknownCondition":
                if (mf != null) mf.setUnknownCondition(UnknownCondition.fromStringValue((String) value));
                break;
            case "displayValues":
                if (mf != null) mf.setDisplayValues((Map<String, String>)value);
                break;
            case "displayOrder":
                if (mf != null)
                    mf.setDisplayOrder((List<String>) value); // value order
                else
                    af.setDisplayOrder((List<String>) value); // annotation order
                break;
            default:
                throw new IllegalStateException("Unknown custom property: " + key);
            }
        }

        public void set(CustomProps props) {
            for (Map.Entry<String, Object> entry : props.asMap().entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
        }

        @Override
        public Map<String, Object> asMap() {
            MetadataField mf = FieldImpl.this instanceof MetadataField ? (MetadataField) FieldImpl.this : null;
            if (mf == null) {
                return Map.of(
                    "displayName", displayName(),
                    "description", description()
                );
            } else {
                return Map.of(
                    "displayName", displayName(),
                    "description", description(),
                    "uiType", mf.uiType(),
                    "unknownValue", mf.unknownValue(),
                    "unknownCondition", mf.unknownCondition().toString(),
                    "displayValues", mf.displayValues(),
                    "displayOrder", mf.displayOrder()
                );
            }
        }
    }
}

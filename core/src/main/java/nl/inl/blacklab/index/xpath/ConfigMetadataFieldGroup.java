package nl.inl.blacklab.index.xpath;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for a group of metadata fields.
 *
 * These could e.g. be shown together on a tab in a user interface.
 */
public class ConfigMetadataFieldGroup {

	/** Group name (often displayed on tab or in list) */
    private String name;

    /** Fields in this group */
    private List<String> fields = new ArrayList<>();

    /** Add any fields not yet in any group to this one? */
    private boolean addRemainingFields = false;

    public ConfigMetadataFieldGroup() {
    }

    public ConfigMetadataFieldGroup(String name) {
        setName(name);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getFields() {
        return fields;
    }

    public void addField(String field) {
        this.fields.add(field);
    }

    public boolean isAddRemainingFields() {
        return addRemainingFields;
    }

    public void setAddRemainingFields(boolean addRemainingFields) {
        this.addRemainingFields = addRemainingFields;
    }


}
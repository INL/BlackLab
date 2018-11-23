package nl.inl.blacklab.indexers.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for how to group annotations for an annotated field.
 */
public class ConfigAnnotationGroups {

    /** Field name */
    private String name;

    /** Annotation groups */
    private List<ConfigAnnotationGroup> groups = new ArrayList<>();

    public ConfigAnnotationGroups() {
    }

    public ConfigAnnotationGroups(String name) {
        setName(name);
    }

    public ConfigAnnotationGroups copy() {
        ConfigAnnotationGroups cp = new ConfigAnnotationGroups(name);
        for (ConfigAnnotationGroup group: groups) {
            cp.addGroup(group.copy());
        }
        return cp;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ConfigAnnotationGroup> getGroups() {
        return Collections.unmodifiableList(groups);
    }

    public void addGroup(ConfigAnnotationGroup group) {
        this.groups.add(group);
    }

    public void addGroups(List<ConfigAnnotationGroup> groups) {
        this.groups.addAll(groups);
    }

    @Override
    public String toString() {
        return "ConfigAnnotationGroups [name=" + name + "]";
    }
    
}

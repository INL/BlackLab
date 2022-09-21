package nl.inl.blacklab.search.indexmetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

import com.fasterxml.jackson.annotation.JsonProperty;

import nl.inl.blacklab.indexers.config.ConfigAnnotatedField;
import nl.inl.blacklab.indexers.config.ConfigAnnotation;
import nl.inl.blacklab.indexers.config.ConfigStandoffAnnotations;
import nl.inl.blacklab.search.BlackLabIndex;

@XmlAccessorType(XmlAccessType.FIELD)
public final class AnnotatedFieldsImpl implements AnnotatedFields, Freezable {

    /** The annotated fields in our index */
    @JsonProperty("fields")
    private final Map<String, AnnotatedFieldImpl> annotatedFields;
    
    /**
     * The main contents field in our index. This is either the annotated field with
     * the name "contents", or if that doesn't exist, the first annotated field found.
     */
    @XmlTransient
    private AnnotatedFieldImpl mainAnnotatedField;

    /**
     * The main contents field in our index. This is either the annotated field with
     * the name "contents", or if that doesn't exist, the first annotated field found.
     */
    @JsonProperty("mainAnnotatedField")
    private String mainAnnotatedFieldName;

    @XmlTransient
    private CustomPropsMap topLevelCustom;

    /** Frozen or not? */
    @XmlTransient
    FreezeStatus freezeStatus = new FreezeStatus();

    public AnnotatedFieldsImpl() {
        annotatedFields = new TreeMap<>();
    }

    @Override
    public AnnotatedField main() {
        return mainAnnotatedField;
    }

    @Override
    public Iterator<AnnotatedField> iterator() {
        Iterator<AnnotatedFieldImpl> it = annotatedFields.values().iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public AnnotatedField next() {
                return it.next();
            }
        };
    }

    @Override
    public Stream<AnnotatedField> stream() {
        return annotatedFields.values().stream().map(f -> f);
    }

    @Override
    public AnnotatedFieldImpl get(String fieldName) {
        if (!annotatedFields.containsKey(fieldName))
            throw new IllegalArgumentException("Annotated field '" + fieldName + "' not found!");
        return annotatedFields.get(fieldName);
    }

    @Override
    public boolean exists(String fieldName) {
        return annotatedFields.containsKey(fieldName);
    }

    public boolean freeze() {
        boolean b = freezeStatus.freeze();
        if (b)
            annotatedFields.values().forEach(AnnotatedFieldImpl::freeze);
        return b;
    }

    @Override
    public boolean isFrozen() {
        return freezeStatus.isFrozen();
    }

    public void put(String fieldName, AnnotatedFieldImpl fieldDesc) {
        annotatedFields.put(fieldName, fieldDesc);
        if ( (mainAnnotatedField == null && mainAnnotatedFieldName == null) || fieldName.equals("contents")) {
            mainAnnotatedFieldName = fieldName;
            mainAnnotatedField = fieldDesc;
        }
    }

    public void setMainAnnotatedField(AnnotatedFieldImpl mainAnnotatedField) {
        this.mainAnnotatedField = mainAnnotatedField;
        this.mainAnnotatedFieldName = mainAnnotatedField == null ? null : mainAnnotatedField.name();
    }

    private Map<String, AnnotationGroups> annotationGroupsPerField() {
        Map<String, AnnotationGroups> map = (Map<String, AnnotationGroups>) topLevelCustom.get("annotationGroups");
        if (map == null) {
            ensureNotFrozen();
            map = new LinkedHashMap<>();
            topLevelCustom.put("annotationGroups", map);
        }
        return map;
    }

    public void clearAnnotationGroups() {
        annotationGroupsPerField().clear();
    }

    public void putAnnotationGroups(String fieldName, AnnotationGroups annotationGroups) {
        annotationGroupsPerField().put(fieldName, annotationGroups);
    }
    
    @Override
    public AnnotationGroups annotationGroups(String fieldName) {
        return annotationGroupsPerField().get(fieldName);
    }

    public void fixAfterDeserialization(BlackLabIndex index, IndexMetadataIntegrated metadata) {
        setTopLevelCustom(metadata.custom());

        CustomProps custom = metadata.custom();
        if (custom.containsKey("annotationGroups")) {
            clearAnnotationGroups();
            Map<String, List<Map<String, Object>>> groupingsPerField = custom.get("annotationGroups", Collections.emptyMap());
            for (Map.Entry<String, List<Map<String, Object>>> entry: groupingsPerField.entrySet()) {
                String fieldName = entry.getKey();
                List<Map<String, Object>> groups = entry.getValue();
                List<AnnotationGroup> annotationGroups = extractAnnotationGroups(fieldName, groups);
                putAnnotationGroups(fieldName, new AnnotationGroups(fieldName, annotationGroups));
            }
        }

        for (Map.Entry<String, AnnotatedFieldImpl> e: annotatedFields.entrySet()) {
            e.getValue().fixAfterDeserialization(index, e.getKey());
        }
        setMainAnnotatedField(annotatedFields.get(mainAnnotatedFieldName));

    }

    private static List<AnnotationGroup> extractAnnotationGroups(String fieldName,
            List<Map<String, Object>> groups) {
        List<AnnotationGroup> annotationGroups = new ArrayList<>();
        for (Map<String, Object> group: groups) {
            String groupName = (String)group.getOrDefault("name", "UNKNOWN");
            List<String> annotations = (List<String>)group.getOrDefault( "annotations", Collections.emptyList());
            boolean addRemainingAnnotations = (boolean)group.getOrDefault("addRemainingAnnotations", false);
            annotationGroups.add(new AnnotationGroup(fieldName, groupName, annotations,
                    addRemainingAnnotations));
        }
        return annotationGroups;
    }

    void setTopLevelCustom(CustomPropsMap custom) {
        this.topLevelCustom = custom;
    }

    public void addFromConfig(ConfigAnnotatedField f) {
        AnnotatedFieldImpl annotatedField = new AnnotatedFieldImpl(f.getName());
        annotatedField.putCustom("displayName", f.getDisplayName());
        annotatedField.putCustom("description", f.getDescription());
        List<String> displayOrder = new ArrayList<>();

        if (!f.getAnnotations().isEmpty())
            annotatedField.setMainAnnotationName(f.getAnnotations().values().iterator().next().getName());
        int n = 0;
        boolean hasOffsets;
        for (ConfigAnnotation configAnnot: f.getAnnotations().values()) {
            hasOffsets = n == 0; // first annotation gets offsets
            addAnnotationInfo(annotatedField, configAnnot, hasOffsets, displayOrder);
            n++;
        }
        for (ConfigStandoffAnnotations standoff: f.getStandoffAnnotations()) {
            for (ConfigAnnotation configAnnot: standoff.getAnnotations().values()) {
                hasOffsets = n == 0; // first annotation gets offsets
                addAnnotationInfo(annotatedField, configAnnot, hasOffsets, displayOrder);
                n++;
            }
        }
        annotatedField.putCustom("displayOrder", displayOrder);
        put(annotatedField.name(), annotatedField);
    }

    public void addAnnotationInfo(AnnotatedFieldImpl f, ConfigAnnotation config, boolean hasOffsets, List<String> displayOrder) {
        AnnotationImpl annotation = new AnnotationImpl(f, config.getName());
        CustomPropsMap custom = annotation.custom();
        custom.put("displayName", config.getDisplayName());
        custom.put("description", config.getDescription());

        annotation.createSensitivities(config.getSensitivitySetting());
        if (hasOffsets)
            annotation.setOffsetsMatchSensitivity(annotation.mainSensitivity().sensitivity());

        f.putAnnotation(annotation);
        displayOrder.add(annotation.name());
    }
}

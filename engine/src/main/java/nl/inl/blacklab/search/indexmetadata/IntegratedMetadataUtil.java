package nl.inl.blacklab.search.indexmetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Methods for serializing and deserializing index metadata, as well as creating the initial metadata.
 *
 * Only used for the integrated index format (newer version of the metadata structure, with slightly
 * different fields).
 */
class IntegratedMetadataUtil {
    public static final String LATEST_INDEX_FORMAT = "4";

    public static List<AnnotationGroup> extractAnnotationGroups(AnnotatedFieldsImpl annotatedFields, String fieldName,
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
}

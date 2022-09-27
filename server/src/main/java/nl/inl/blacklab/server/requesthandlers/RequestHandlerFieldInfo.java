package nl.inl.blacklab.server.requesthandlers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.Annotations;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.WebserviceOperations;

/**
 * Get information about a field in the index.
 */
public class RequestHandlerFieldInfo extends RequestHandler {

    public RequestHandlerFieldInfo(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public boolean isCacheAllowed() {
        return false; // Because reindexing might change something
    }

    @Override
    public int handle(DataStream ds) throws BlsException {
        int i = urlPathInfo.indexOf('/');
        String fieldName = i >= 0 ? urlPathInfo.substring(0, i) : urlPathInfo;
        if (fieldName.length() == 0) {
            throw new BadRequest("UNKNOWN_OPERATION",
                    "Bad URL. Either specify a field name to show information about, or remove the 'fields' part to get general index information.");
        }

        BlackLabIndex blIndex = blIndex();
        IndexMetadata indexMetadata = blIndex.metadata();
        if (indexMetadata.annotatedFields().exists(fieldName)) {
            // Annotated field
            Collection<String> setShowValuesFor = params.getListValuesFor();
            AnnotatedField fieldDesc = indexMetadata.annotatedField(fieldName);
            if (!fieldDesc.isDummyFieldToStoreLinkedDocuments()) {
                describeAnnotatedField(ds, indexName, fieldDesc, blIndex, setShowValuesFor);
            } else {
                // skip this, not really an annotated field, just exists to store linked (metadata) document.
            }
        } else {
            // Metadata field
            MetadataField fieldDesc = indexMetadata.metadataField(fieldName);
            Map<String, Integer> fieldValues = WebserviceOperations.getFieldValuesInOrder(fieldDesc);
            DataStreamUtil.metadataField(ds, indexName, fieldDesc, true, fieldValues);
        }

        // Remove any empty settings
        //response.removeEmptyMapValues();

        return HTTP_OK;
    }

    public static String sensitivitySettingDesc(Annotation annotation) {
        String sensitivityDesc;
        if (annotation.hasSensitivity(MatchSensitivity.SENSITIVE)) {
            if (annotation.hasSensitivity(MatchSensitivity.INSENSITIVE)) {
                if (annotation.hasSensitivity(MatchSensitivity.CASE_INSENSITIVE)) {
                    sensitivityDesc = "CASE_AND_DIACRITICS_SEPARATE";
                } else {
                    sensitivityDesc = "SENSITIVE_AND_INSENSITIVE";
                }
            } else {
                sensitivityDesc = "ONLY_SENSITIVE";
            }
        } else {
            sensitivityDesc = "ONLY_INSENSITIVE";
        }
        return sensitivityDesc;
    }

    static class ResultAnnotationInfo {

        Annotation annotation;

        boolean showValues;

        Set<String> terms;

        boolean valueListComplete;

        List<String> subannot;

        public String parentAnnot;

    }

    public static void describeAnnotatedField(DataStream ds, String indexName,
            AnnotatedField fieldDesc, BlackLabIndex index, Collection<String> showValuesFor) {
        ds.startMap();
        if (indexName != null)
            ds.entry("indexName", indexName);
        Annotations annotations = fieldDesc.annotations();
        ds
                .entry("fieldName", fieldDesc.name())
                .entry("isAnnotatedField", true)
                .entry("displayName", fieldDesc.displayName())
                .entry("description", fieldDesc.description())
                .entry("hasContentStore", fieldDesc.hasContentStore())
                .entry("hasXmlTags", fieldDesc.hasXmlTags());
        ds.entry("mainAnnotation", annotations.main().name());
        ds.startEntry("displayOrder").startList();
        annotations.stream().map(Annotation::name).forEach(id -> ds.item("fieldName", id));
        ds.endList().endEntry();

        Map<String, ResultAnnotationInfo> annotInfos = new LinkedHashMap<>();
        for (Annotation annotation: annotations) {
            ResultAnnotationInfo ai = new ResultAnnotationInfo();
            ai.annotation = annotation;
            if (!index.isEmpty()) {
                ai.showValues = annotationMatches(annotation.name(), showValuesFor);
                if (ai.showValues) {
                    boolean[] valueListCompleteArray = {
                            true }; // array because we have to access them from the closures
                    ai.terms = WebserviceOperations.getTerms(index, annotation, valueListCompleteArray);
                    ai.valueListComplete = valueListCompleteArray[0];
                }
                ai.subannot = new ArrayList<>();
                for (String name: annotation.subannotationNames()) {
                    ai.subannot.add(name);
                }
                if (annotation.isSubannotation()) {
                    ai.parentAnnot = annotation.parentAnnotation().name();
                }
                annotInfos.put(annotation.name(), ai);
            }
        }

        ds.startEntry("annotations").startMap();
        for (Map.Entry<String, ResultAnnotationInfo> e: annotInfos.entrySet()) {
            ds.startAttrEntry("annotation", "name", e.getKey()).startMap();
            ResultAnnotationInfo ai = e.getValue();
            Annotation annotation = ai.annotation;
            AnnotationSensitivity offsetsSensitivity = annotation.offsetsSensitivity();
            String offsetsAlternative = offsetsSensitivity == null ? "" :
                    offsetsSensitivity.sensitivity().luceneFieldSuffix();
            ds
                    .entry("displayName", annotation.displayName())
                    .entry("description", annotation.description())
                    .entry("uiType", annotation.uiType())
                    .entry("hasForwardIndex", annotation.hasForwardIndex())
                    .entry("sensitivity", sensitivitySettingDesc(annotation))
                    .entry("offsetsAlternative", offsetsAlternative)
                    .entry("isInternal", annotation.isInternal());
            if (!index.isEmpty() /*|| !(index instanceof BlackLabIndexIntegrated)*/) {
                if (annotationMatches(annotation.name(), showValuesFor)) {
                    ds.startEntry("values").startList();

                    boolean[] valueListCompleteArray = { true }; // array because we have to access them from the closures
                    Set<String> terms = WebserviceOperations.getTerms(index, annotation, valueListCompleteArray);
                    boolean valueListComplete = valueListCompleteArray[0];

                    for (String term: terms) {
                        ds.item("value", term);
                    }
                    ds.endList().endEntry();
                    ds.entry("valueListComplete", valueListComplete);
                }
                if (!annotation.subannotationNames().isEmpty()) {
                    ds.startEntry("subannotations").startList();
                    for (String name: annotation.subannotationNames()) {
                        ds.item("subannotation", name);
                    }
                    ds.endList().endEntry();
                }
                if (annotation.isSubannotation()) {
                    ds.entry("parentAnnotation", annotation.parentAnnotation().name());
                }
            }
            ds.endMap().endAttrEntry();
        }
        ds.endMap().endEntry();
        ds.endMap();
    }

    public static boolean annotationMatches(String name, Collection<String> showValuesFor) {
        //return showValuesFor.contains(name);
        for (String expr: showValuesFor) {
            if (name.matches("^" + expr + "$")) {
                return true;
            }
        }
        return false;
    }

}

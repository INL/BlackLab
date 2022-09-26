package nl.inl.blacklab.server.lib;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.document.Document;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocGroupProperty;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFields;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.MetadataFieldGroup;
import nl.inl.blacklab.search.indexmetadata.MetadataFields;
import nl.inl.blacklab.search.results.DocGroup;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.server.exceptions.BlsException;

public class WebserviceOperations {

    private WebserviceOperations() {}

    public static ResultDocContents getDocContents(SearchCreator params, String docPid)
            throws BlsException, InvalidQuery {
        return new ResultDocContents(params, docPid);
    }

    /**
     * Returns a list of metadata fields to write out.
     *
     * By default, all metadata fields are returned.
     * Special fields (pidField, titleField, etc...) are always returned.
     *
     * @return a list of metadata fields to write out, as specified by the "listmetadatavalues" query parameter.
     */
    public static Set<MetadataField> getMetadataToWrite(BlackLabIndex index, SearchCreator params) throws BlsException {
        MetadataFields fields = index.metadataFields();
        Set<String> requestedFields = params.getListMetadataValuesFor();

        Set<MetadataField> ret = new HashSet<>();
        ret.add(optCustomField(index.metadata(), "authorField"));
        ret.add(optCustomField(index.metadata(), "dateField"));
        ret.add(optCustomField(index.metadata(), "titleField"));
        ret.add(fields.pidField());
        for (MetadataField field  : fields) {
            if (requestedFields.isEmpty() || requestedFields.contains(field.name())) {
                ret.add(field);
            }
        }
        ret.remove(null); // for missing special fields.
        return ret;
    }

    private static MetadataField optCustomField(IndexMetadata metadata, String propName) {
        String fieldName = metadata.custom().get(propName, "");
        return fieldName.isEmpty() ? null : metadata.metadataFields().get(fieldName);
    }

    private static ResultMetadataGroupInfo getMetadataGroupInfo(BlackLabIndex index) {
        Map<String, ? extends MetadataFieldGroup> metaGroups = index.metadata().metadataFields().groups();
        Set<MetadataField> metadataFieldsNotInGroups = index.metadata().metadataFields().stream()
                .collect(Collectors.toSet());
        for (MetadataFieldGroup metaGroup : metaGroups.values()) {
            for (String fieldName: metaGroup) {
                MetadataField field = index.metadata().metadataFields().get(fieldName);
                metadataFieldsNotInGroups.remove(field);
            }
        }
        List<MetadataField> rest = new ArrayList<>(metadataFieldsNotInGroups);
        rest.sort(Comparator.comparing(a -> a.name().toLowerCase()));
        return new ResultMetadataGroupInfo(metaGroups, rest);
    }

    public static Map<String, List<String>> getMetadataFieldGroupsWithRest(BlackLabIndex index) {
        ResultMetadataGroupInfo metadataGroupInfo = WebserviceOperations.getMetadataGroupInfo(index);

        Map<String, List<String>> metadataFieldGroups = new LinkedHashMap<>();
        boolean addedRemaining = false;
        for (MetadataFieldGroup metaGroup : metadataGroupInfo.getMetaGroups().values()) {
            List<String> metadataFieldGroup = new ArrayList<>();
            for (String field: metaGroup) {
                metadataFieldGroup.add(field);
            }
            if (!addedRemaining && metaGroup.addRemainingFields()) {
                addedRemaining = true;
                List<MetadataField> rest = new ArrayList<>(metadataGroupInfo.getMetadataFieldsNotInGroups());
                rest.sort(Comparator.comparing(a -> a.name().toLowerCase()));
                for (MetadataField field: rest) {
                    metadataFieldGroup.add(field.name());
                }
            }
            metadataFieldGroups.put(metaGroup.name(), metadataFieldGroup);
        }
        return metadataFieldGroups;
    }

    public static ResultDocInfo getDocInfo(BlackLabIndex index, String docPid, Set<MetadataField> metadataToWrite)
            throws BlsException {
        return new ResultDocInfo(index, docPid, null, metadataToWrite);
    }

    public static ResultDocInfo getDocInfo(BlackLabIndex index, Document document, Set<MetadataField> metadataToWrite)
            throws BlsException {
        return new ResultDocInfo(index, null, document, metadataToWrite);
    }

    public static Map<String, String> getDocFields(IndexMetadata indexMetadata) {
        Map<String, String> docFields = new LinkedHashMap<>();
        MetadataField pidField = indexMetadata.metadataFields().pidField();
        if (pidField != null)
            docFields.put("pidField", pidField.name());
        for (String propName: List.of("titleField", "authorField", "dateField")) {
            String fieldName = indexMetadata.custom().get(propName, "");
            if (!fieldName.isEmpty())
                docFields.put(propName, fieldName);
        }
        return docFields;
    }

    public static Map<String, String> getMetaDisplayNames(BlackLabIndex index) {
        Map<String, String> metaDisplayNames = new LinkedHashMap<>();
        for (MetadataField f: index.metadata().metadataFields()) {
            String displayName = f.displayName();
            if (!f.name().equals("lengthInTokens") && !f.name().equals("mayView")) {
                metaDisplayNames.put(f.name(),displayName);
            }
        }
        return metaDisplayNames;
    }

    /**
     * Get the pid for the specified document
     *
     * @param index where we got this document from
     * @param luceneDocId Lucene document id
     * @param document the document object
     * @return the pid string (or Lucene doc id in string form if index has no pid
     *         field)
     */
    public static String getDocumentPid(BlackLabIndex index, int luceneDocId, Document document) {
        MetadataField pidField = index.metadataFields().pidField();
        String pid = pidField == null ? null : document.get(pidField.name());
        if (pid == null)
            return Integer.toString(luceneDocId);
        return pid;
    }

    /**
     * Returns the annotations to write out.
     *
     * By default, all annotations are returned.
     * Annotations are returned in requested order, or in their definition/display order.
     *
     * @return the annotations to write out, as specified by the (optional) "listvalues" query parameter.
     */
    public static List<Annotation> getAnnotationsToWrite(BlackLabIndex index, WebserviceParams params) throws BlsException {
        AnnotatedFields fields = index.annotatedFields();
        Set<String> requestedAnnotations = params.getListValuesFor();

        List<Annotation> ret = new ArrayList<>();
        for (AnnotatedField f : fields) {
            for (Annotation a : f.annotations()) {
                if (requestedAnnotations.isEmpty() || requestedAnnotations.contains(a.name())) {
                    ret.add(a);
                }
            }
        }

        return ret;
    }

    public static Map<String, ResultDocInfo> getDocInfos(BlackLabIndex index, Map<Integer, Document> luceneDocs,
            Set<MetadataField> metadataFieldsToList) {
        Map<String, ResultDocInfo> docInfos = new LinkedHashMap<>();
        for (Map.Entry<Integer, Document> e: luceneDocs.entrySet()) {
            Integer docId = e.getKey();
            Document luceneDoc = e.getValue();
            String pid = getDocumentPid(index, docId, luceneDoc);
            ResultDocInfo docInfo = getDocInfo(index, luceneDoc, metadataFieldsToList);
            docInfos.put(pid, docInfo);
        }
        return docInfos;
    }

    public static Map<String, List<Pair<String, Long>>> getFacetInfo(Map<DocProperty, DocGroups> counts) {
        Map<String, List<Pair<String,  Long>>> facetInfo = new LinkedHashMap<>();
        for (Map.Entry<DocProperty, DocGroups> e : counts.entrySet()) {
            DocProperty facetBy = e.getKey();
            DocGroups facetCounts = e.getValue();
            facetCounts = facetCounts.sort(DocGroupProperty.size());
            String facetName = facetBy.name();
            List<Pair<String,  Long>> facetItems = new ArrayList<>();
            int n = 0, maxFacetValues = 10;
            int totalSize = 0;
            for (DocGroup count : facetCounts) {
                String value = count.identity().toString();
                long size = count.size();
                facetItems.add(Pair.of(value, size));
                totalSize += size;
                n++;
                if (n >= maxFacetValues)
                    break;
            }
            if (totalSize < facetCounts.sumOfGroupSizes()) {
                facetItems.add(Pair.of("[REST]", facetCounts.sumOfGroupSizes() - totalSize));
            }
            facetInfo.put(facetName, facetItems);
        }
        return facetInfo;
    }
}

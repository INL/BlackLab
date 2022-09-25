package nl.inl.blacklab.server.lib;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.MetadataFieldGroup;
import nl.inl.blacklab.search.indexmetadata.MetadataFields;
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

    public static ResultMetadataGroupInfo getMetadataGroupInfo(BlackLabIndex index) {
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
        ResultMetadataGroupInfo info = new ResultMetadataGroupInfo(metaGroups, rest);
        return info;
    }

    public static ResultDocInfo getDocInfo(BlackLabIndex index, SearchCreator params, String docPid)
            throws BlsException {
        return new ResultDocInfo(params, docPid, WebserviceOperations.getMetadataToWrite(index, params));
    }

}

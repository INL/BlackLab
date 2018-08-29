package nl.inl.blacklab.server.requesthandlers;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.index.IndexListener;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationGroup;
import nl.inl.blacklab.search.indexmetadata.AnnotationGroups;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.MetadataFieldGroup;
import nl.inl.blacklab.search.indexmetadata.MetadataFieldGroups;
import nl.inl.blacklab.search.indexmetadata.MetadataFields;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.index.Index.IndexStatus;
import nl.inl.blacklab.server.jobs.User;

/**
 * Get information about the structure of an index.
 */
public class RequestHandlerIndexMetadata extends RequestHandler {

    public RequestHandlerIndexMetadata(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public boolean isCacheAllowed() {
        return false; // because status might change (or you might reindex)
    }
    
    String optSpecialFieldName(MetadataFields metadataFields, String type) {
        MetadataField specialField = metadataFields.special(type);
        return specialField == null ? "" : specialField.name();
    }

    @Override
    public int handle(DataStream ds) throws BlsException {
        Index index = indexMan.getIndex(indexName);
        synchronized (index) {
            BlackLabIndex blIndex = index.blIndex();
            IndexMetadata indexMetadata = blIndex.metadata();

            // Assemble response
            IndexStatus status = indexMan.getIndex(indexName).getStatus();
            ds.startMap()
                    .entry("indexName", indexName)
                    .entry("displayName", indexMetadata.displayName())
                    .entry("description", indexMetadata.description())
                    .entry("status", status)
                    .entry("contentViewable", indexMetadata.contentViewable())
                    .entry("textDirection", indexMetadata.textDirection().getCode());

            if (status.equals(IndexStatus.INDEXING)) {
                IndexListener indexProgress = index.getIndexerListener();
                synchronized (indexProgress) {
                    ds.startEntry("indexProgress").startMap()
                            .entry("filesProcessed", indexProgress.getFilesProcessed())
                            .entry("docsDone", indexProgress.getDocsDone())
                            .entry("tokensProcessed", indexProgress.getTokensProcessed())
                            .endMap().endEntry();
                }
            }

            String formatIdentifier = indexMetadata.documentFormat();
            if (formatIdentifier != null && formatIdentifier.length() > 0)
                ds.entry("documentFormat", formatIdentifier);
            if (indexMetadata.tokenCount() > 0)
                ds.entry("tokenCount", indexMetadata.tokenCount());

            ds.startEntry("versionInfo").startMap()
                    .entry("blackLabBuildTime", indexMetadata.indexBlackLabBuildTime())
                    .entry("blackLabVersion", indexMetadata.indexBlackLabVersion())
                    .entry("indexFormat", indexMetadata.indexFormat())
                    .entry("timeCreated", indexMetadata.timeCreated())
                    .entry("timeModified", indexMetadata.timeModified())
                    .endMap().endEntry();

            MetadataFields fields = indexMetadata.metadataFields();
            ds.startEntry("fieldInfo").startMap()
                    .entry("pidField", optSpecialFieldName(fields, MetadataFields.PID))
                    .entry("titleField", optSpecialFieldName(fields, MetadataFields.TITLE))
                    .entry("authorField", optSpecialFieldName(fields, MetadataFields.AUTHOR))
                    .entry("dateField", optSpecialFieldName(fields, MetadataFields.DATE))
                    .endMap().endEntry();

            ds.startEntry("complexFields").startMap();
            // Annotated fields
            for (AnnotatedField field: indexMetadata.annotatedFields()) {
                ds.startAttrEntry("complexField", "name", field.name());

                Set<String> setShowValuesFor = searchParam.listValuesFor();
                Set<String> setShowSubpropsFor = searchParam.listSubpropsFor();
                RequestHandlerFieldInfo.describeAnnotatedField(ds, null, field, blIndex, setShowValuesFor,
                        setShowSubpropsFor);

                ds.endAttrEntry();
            }
            ds.endMap().endEntry();

            ds.startEntry("metadataFields").startMap();
            // Metadata fields
            //DataObjectMapAttribute doMetaFields = new DataObjectMapAttribute("metadataField", "name");
            for (MetadataField f: fields) {
                ds.startAttrEntry("metadataField", "name", f.name());
                RequestHandlerFieldInfo.describeMetadataField(ds, null, f, true);
                ds.endAttrEntry();
            }
            ds.endMap().endEntry();

            MetadataFieldGroups metaGroups = indexMetadata.metadataFields().groups();
            Set<MetadataField> metadataFieldsNotInGroups = new HashSet<>(indexMetadata.metadataFields().stream().collect(Collectors.toSet()));
            for (MetadataFieldGroup metaGroup : metaGroups) {
                for (MetadataField field: metaGroup) {
                    metadataFieldsNotInGroups.remove(field);
                }
            }
            ds.startEntry("metadataFieldGroups").startList();
            boolean addedRemaining = false;
            for (MetadataFieldGroup metaGroup : metaGroups) {
                ds.startItem("metadataFieldGroup").startMap();
                ds.entry("name", metaGroup.name());
                ds.startEntry("fields").startList();
                for (MetadataField field: metaGroup) {
                    ds.item("field", field.name());
                }
                if (!addedRemaining && metaGroup.addRemainingFields()) {
                    addedRemaining = true;
                    for (MetadataField field: metadataFieldsNotInGroups) {
                        ds.item("field", field.name());
                    }
                }
                ds.endList().endEntry();
                ds.endMap().endItem();
            }
            ds.endList().endEntry();
            
            ds.startEntry("annotationGroups").startMap();
            for (AnnotatedField f: indexMetadata.annotatedFields()) {
                AnnotationGroups groups = indexMetadata.annotatedFields().annotationGroups(f.name());
                Set<Annotation> annotationsNotInGroups = new HashSet<>(f.annotations().stream().collect(Collectors.toList()));
                for (AnnotationGroup group : groups) {
                    for (Annotation annotation: group) {
                        annotationsNotInGroups.remove(annotation);
                    }
                }
                ds.startAttrEntry("annotatedField", "name", f.name()).startList();
                boolean addedRemainingAnnots = false;
                for (AnnotationGroup group : groups) {
                    ds.startItem("metadataFieldGroup").startMap();
                    ds.entry("name", group.groupName());
                    ds.startEntry("annotations").startList();
                    for (Annotation annotation: group) {
                        ds.item("annotation", annotation.name());
                    }
                    if (!addedRemainingAnnots && group.addRemainingAnnotations()) {
                        addedRemainingAnnots = true;
                        for (Annotation annotation: annotationsNotInGroups) {
                            if (!annotation.isInternal())
                                ds.item("annotation", annotation.name());
                        }
                    }
                    ds.endList().endEntry();
                    ds.endMap().endItem();
                }
                ds.endList().endAttrEntry();
            }
            ds.endMap().endEntry();

            // Remove any empty settings
            //response.removeEmptyMapValues();

            ds.endMap();

            return HTTP_OK;
        }
    }

}

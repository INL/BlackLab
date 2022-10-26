package nl.inl.blacklab.server.requesthandlers;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationGroup;
import nl.inl.blacklab.search.indexmetadata.AnnotationGroups;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.ResultIndexMetadata;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.results.ResultAnnotatedField;
import nl.inl.blacklab.server.lib.results.ResultMetadataField;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;

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

    @Override
    public int handle(DataStream ds) throws BlsException {
        ResultIndexMetadata result = WebserviceOperations.indexMetadata(params);
        dstreamIndexMetadataResponse(ds, result);
        return HTTP_OK;
    }

    private void dstreamIndexMetadataResponse(DataStream ds, ResultIndexMetadata result) {
        IndexMetadata metadata = result.getMetadata();
        ds.startMap();
        {
            ds.entry("indexName", indexName)
                    .entry("displayName", metadata.custom().get("displayName", ""))
                    .entry("description", metadata.custom().get("description", ""))
                    .entry("status", result.getProgress().getIndexStatus())
                    .entry("contentViewable", metadata.contentViewable())
                    .entry("textDirection", metadata.custom().get("textDirection", "ltr"));

            DStream.indexProgress(ds, result.getProgress());
            ds.entry("tokenCount", metadata.tokenCount());
            ds.entry("documentCount", metadata.documentCount());

            ds.startEntry("versionInfo").startMap()
                    .entry("blackLabBuildTime", metadata.indexBlackLabBuildTime())
                    .entry("blackLabVersion", metadata.indexBlackLabVersion())
                    .entry("indexFormat", metadata.indexFormat())
                    .entry("timeCreated", metadata.timeCreated())
                    .entry("timeModified", metadata.timeModified())
                    .endMap().endEntry();

            ds.startEntry("fieldInfo").startMap()
                    .entry("pidField", metadata.metadataFields().pidField() == null ? "" : metadata.metadataFields().pidField())
                    .entry("titleField", metadata.custom().get("titleField", ""))
                    .entry("authorField", metadata.custom().get("authorField", ""))
                    .entry("dateField", metadata.custom().get("dateField", ""))
                    .endMap().endEntry();

            ds.startEntry("annotatedFields").startMap();
            for (ResultAnnotatedField annotatedField: result.getAnnotatedFields()) {
                ds.startAttrEntry("annotatedField", "name", annotatedField.getFieldDesc().name());
                {
                    DStream.annotatedField(ds, annotatedField);
                }
                ds.endAttrEntry();
            }
            ds.endMap().endEntry();

            ds.startEntry("metadataFields").startMap();
            for (ResultMetadataField metadataField: result.getMetadataFields()) {
                ds.startAttrEntry("metadataField", "name", metadataField.getFieldDesc().name());
                {
                    DStream.metadataField(ds, metadataField);
                }
                ds.endAttrEntry();
            }
            ds.endMap().endEntry();

            DStream.metadataGroupInfo(ds, result.getMetadataFieldGroups());

            ds.startEntry("annotationGroups").startMap();
            for (AnnotatedField f: metadata.annotatedFields()) {
                AnnotationGroups groups = metadata.annotatedFields().annotationGroups(f.name());
                if (groups != null) {
                    @SuppressWarnings("FuseStreamOperations") // LinkedHashSet - preserve order!
                    Set<Annotation> annotationsNotInGroups = new LinkedHashSet<>(
                            f.annotations().stream().collect(Collectors.toList()));
                    for (AnnotationGroup group: groups) {
                        for (String annotationName: group) {
                            Annotation annotation = f.annotation(annotationName);
                            annotationsNotInGroups.remove(annotation);
                        }
                    }
                    ds.startAttrEntry("annotatedField", "name", f.name()).startList();
                    boolean addedRemainingAnnots = false;
                    for (AnnotationGroup group: groups) {
                        ds.startItem("annotationGroup").startMap();
                        ds.entry("name", group.groupName());
                        ds.startEntry("annotations").startList();
                        for (String annotation: group) {
                            ds.item("annotation", annotation);
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
            }
            ds.endMap().endEntry();
        }
        ds.endMap();
    }

}

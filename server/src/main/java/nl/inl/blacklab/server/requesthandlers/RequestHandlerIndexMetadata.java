package nl.inl.blacklab.server.requesthandlers;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.index.IndexListener;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.indexmetadata.ComplexFieldDesc;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.nint.MetadataField;
import nl.inl.blacklab.search.indexmetadata.nint.MetadataFields;
import nl.inl.blacklab.search.indexmetadata.nint.MetadataFields.MetadataFieldGroup;
import nl.inl.blacklab.search.indexmetadata.nint.MetadataFields.MetadataFieldGroups;
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
            Searcher searcher = index.getSearcher();
            IndexMetadata indexMetadata = searcher.getIndexMetadata();

            // Assemble response
            IndexStatus status = indexMan.getIndex(indexName).getStatus();
            ds.startMap()
                    .entry("indexName", indexName)
                    .entry("displayName", indexMetadata.getDisplayName())
                    .entry("description", indexMetadata.getDescription())
                    .entry("status", status)
                    .entry("contentViewable", indexMetadata.contentViewable())
                    .entry("textDirection", indexMetadata.getTextDirection().getCode());

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

            String formatIdentifier = indexMetadata.getDocumentFormat();
            if (formatIdentifier != null && formatIdentifier.length() > 0)
                ds.entry("documentFormat", formatIdentifier);
            if (indexMetadata.getTokenCount() > 0)
                ds.entry("tokenCount", indexMetadata.getTokenCount());

            ds.startEntry("versionInfo").startMap()
                    .entry("blackLabBuildTime", indexMetadata.getIndexBlackLabBuildTime())
                    .entry("blackLabVersion", indexMetadata.getIndexBlackLabVersion())
                    .entry("indexFormat", indexMetadata.getIndexFormat())
                    .entry("timeCreated", indexMetadata.getTimeCreated())
                    .entry("timeModified", indexMetadata.getTimeModified())
                    .endMap().endEntry();

            MetadataFields fields = indexMetadata.metadataFields();
            ds.startEntry("fieldInfo").startMap()
                    .entry("pidField", optSpecialFieldName(fields, MetadataFields.PID))
                    .entry("titleField", optSpecialFieldName(fields, MetadataFields.TITLE))
                    .entry("authorField", optSpecialFieldName(fields, MetadataFields.AUTHOR))
                    .entry("dateField", optSpecialFieldName(fields, MetadataFields.DATE))
                    .endMap().endEntry();

            ds.startEntry("complexFields").startMap();
            // Complex fields
            //DataObjectMapAttribute doComplexFields = new DataObjectMapAttribute("complexField", "name");
            for (String name : indexMetadata.getComplexFields()) {
                ds.startAttrEntry("complexField", "name", name);

                Set<String> setShowValuesFor = searchParam.listValuesFor();
                Set<String> setShowSubpropsFor = searchParam.listSubpropsFor();
                ComplexFieldDesc fieldDesc = indexMetadata.getComplexFieldDesc(name);
                RequestHandlerFieldInfo.describeComplexField(ds, null, name, fieldDesc, searcher, setShowValuesFor,
                        setShowSubpropsFor);

                ds.endAttrEntry();
            }
            ds.endMap().endEntry();

            ds.startEntry("metadataFields").startMap();
            // Metadata fields
            //DataObjectMapAttribute doMetaFields = new DataObjectMapAttribute("metadataField", "name");
            for (MetadataField f: fields) {
                ds.startAttrEntry("metadataField", "name", f.name());

                MetadataField fd = fields.get(f.name());
                RequestHandlerFieldInfo.describeMetadataField(ds, null, f.name(), fd, true);

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

            // Remove any empty settings
            //response.removeEmptyMapValues();

            ds.endMap();

            return HTTP_OK;
        }
    }

}

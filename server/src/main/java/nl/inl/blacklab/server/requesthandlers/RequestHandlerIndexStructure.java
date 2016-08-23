package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.indexstructure.ComplexFieldDesc;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
import nl.inl.blacklab.search.indexstructure.MetadataFieldDesc;
import nl.inl.blacklab.search.indexstructure.PropertyDesc;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.util.StringUtil;

/**
 * Get information about the structure of an index.
 */
public class RequestHandlerIndexStructure extends RequestHandler {

	public RequestHandlerIndexStructure(BlackLabServer servlet, HttpServletRequest request, User user, String indexName, String urlResource, String urlPathPart) {
		super(servlet, request, user, indexName, urlResource, urlPathPart);
	}

	@Override
	public boolean isCacheAllowed() {
		return false; // because status might change (or you might reindex)
	}

	@Override
	public int handle(DataStream ds) throws BlsException {
		Searcher searcher = getSearcher();
		IndexStructure struct = searcher.getIndexStructure();

		// Assemble response
		ds.startMap()
			.entry("indexName", indexName)
			.entry("displayName", struct.getDisplayName())
			.entry("description", struct.getDescription())
			.entry("status", indexMan.getIndexStatus(indexName))
			.entry("contentViewable", struct.contentViewable());
		String documentFormat = struct.getDocumentFormat();
		if (documentFormat != null && documentFormat.length() > 0)
			ds.entry("documentFormat", documentFormat);
		if (struct.getTokenCount() > 0)
			ds.entry("tokenCount", struct.getTokenCount());

		ds.startEntry("versionInfo").startMap()
			.entry("blackLabBuildTime", struct.getIndexBlackLabBuildTime())
			.entry("indexFormat", struct.getIndexFormat())
			.entry("timeCreated", struct.getTimeCreated())
			.entry("timeModified", struct.getTimeModified())
		.endMap().endEntry();

		ds.startEntry("fieldInfo").startMap()
			.entry("pidField", StringUtil.nullToEmpty(struct.pidField()))
			.entry("titleField", StringUtil.nullToEmpty(struct.titleField()))
			.entry("authorField", StringUtil.nullToEmpty(struct.authorField()))
			.entry("dateField", StringUtil.nullToEmpty(struct.dateField()))
		.endMap().endEntry();

		ds.startEntry("complexFields").startMap();
		// Complex fields
		//DataObjectMapAttribute doComplexFields = new DataObjectMapAttribute("complexField", "name");
		for (String name: struct.getComplexFields()) {
			ds.startAttrEntry("complexField", "name", name).startMap();
			ComplexFieldDesc fieldDesc = struct.getComplexFieldDesc(name);

			ds	.entry("displayName", fieldDesc.getDisplayName())
				.entry("description", fieldDesc.getDescription())
				.entry("mainProperty", fieldDesc.getMainProperty().getName());

			ds.startEntry("basicProperties").startMap();
			//DataObjectMapAttribute doProps = new DataObjectMapAttribute("property", "name");
			for (String propName: fieldDesc.getProperties()) {
				if (propName.equals(ComplexFieldUtil.START_TAG_PROP_NAME) || propName.equals(ComplexFieldUtil.END_TAG_PROP_NAME) ||
					propName.equals(ComplexFieldUtil.PUNCTUATION_PROP_NAME))
					continue; // skip tag properties as we don't search on them directly; they are shown in detailed field info
				PropertyDesc propDesc = fieldDesc.getPropertyDesc(propName);
				ds.startAttrEntry("property", "name", propName).startMap()
					.entry("sensitivity", propDesc.getSensitivity().toString())
				.endMap().endAttrEntry();
			}
			ds.endMap().endEntry();

			ds.endMap().endAttrEntry();
		}
		ds.endMap().endEntry();

		ds.startEntry("metadataFields").startMap();
		// Metadata fields
		//DataObjectMapAttribute doMetaFields = new DataObjectMapAttribute("metadataField", "name");
		for (String name: struct.getMetadataFields()) {
			MetadataFieldDesc fd = struct.getMetadataFieldDesc(name);
			ds.startAttrEntry("metadataField", "name", name).startMap()
				.entry("fieldName", fd.getName())
				.entry("displayName", fd.getDisplayName())
				.entry("type", fd.getType().toString())
				.entry("group", fd.getGroup());
			ds.endMap().endAttrEntry();
		}
		ds.endMap().endEntry();

		// Remove any empty settings
		//response.removeEmptyMapValues();

		ds.endMap();

		return HTTP_OK;
	}

}

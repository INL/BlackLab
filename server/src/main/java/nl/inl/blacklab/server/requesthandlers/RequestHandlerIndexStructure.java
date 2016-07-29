package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.indexstructure.ComplexFieldDesc;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
import nl.inl.blacklab.search.indexstructure.MetadataFieldDesc;
import nl.inl.blacklab.search.indexstructure.PropertyDesc;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.dataobject.DataObjectMapAttribute;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
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
	public Response handle() throws BlsException {
		Searcher searcher = getSearcher();
		IndexStructure struct = searcher.getIndexStructure();

		// Complex fields
		DataObjectMapAttribute doComplexFields = new DataObjectMapAttribute("complexField", "name");
		for (String name: struct.getComplexFields()) {
			ComplexFieldDesc fieldDesc = struct.getComplexFieldDesc(name);
			DataObjectMapElement doComplexField = new DataObjectMapElement();
			doComplexField.put("displayName", fieldDesc.getDisplayName());
			doComplexField.put("description", fieldDesc.getDescription());
			doComplexField.put("mainProperty", fieldDesc.getMainProperty().getName());
			DataObjectMapAttribute doProps = new DataObjectMapAttribute("property", "name");
			for (String propName: fieldDesc.getProperties()) {
				if (propName.equals(ComplexFieldUtil.START_TAG_PROP_NAME) || propName.equals(ComplexFieldUtil.END_TAG_PROP_NAME) ||
					propName.equals(ComplexFieldUtil.PUNCTUATION_PROP_NAME))
					continue; // skip tag properties as we don't search on them directly; they are shown in detailed field info
				PropertyDesc propDesc = fieldDesc.getPropertyDesc(propName);
				DataObjectMapElement doProp = new DataObjectMapElement();
				doProp.put("sensitivity", propDesc.getSensitivity().toString());
				doProps.put(propName, doProp);
			}
			doComplexField.put("basicProperties", doProps);
			doComplexFields.put(name, doComplexField);
		}

		// Metadata fields
		DataObjectMapAttribute doMetaFields = new DataObjectMapAttribute("metadataField", "name");
		for (String name: struct.getMetadataFields()) {
			MetadataFieldDesc fd = struct.getMetadataFieldDesc(name);
			DataObjectMapElement doMetaField = new DataObjectMapElement();
			doMetaField.put("fieldName", fd.getName());
			doMetaField.put("displayName", fd.getDisplayName());
			doMetaField.put("type", fd.getType().toString());
			doMetaField.put("group", fd.getGroup());
			doMetaFields.put(name, doMetaField);
		}

		DataObjectMapElement doVersionInfo = new DataObjectMapElement();
		doVersionInfo.put("blackLabBuildTime", struct.getIndexBlackLabBuildTime());
		doVersionInfo.put("indexFormat", struct.getIndexFormat());
		doVersionInfo.put("timeCreated", struct.getTimeCreated());
		doVersionInfo.put("timeModified", struct.getTimeModified());

		DataObjectMapElement doFieldInfo = new DataObjectMapElement();
		doFieldInfo.put("pidField", StringUtil.nullToEmpty(struct.pidField()));
		doFieldInfo.put("titleField", StringUtil.nullToEmpty(struct.titleField()));
		doFieldInfo.put("authorField", StringUtil.nullToEmpty(struct.authorField()));
		doFieldInfo.put("dateField", StringUtil.nullToEmpty(struct.dateField()));
		doFieldInfo.put("complexFields", doComplexFields);
		doFieldInfo.put("metadataFields", doMetaFields);

		// Assemble response
		DataObjectMapElement response = new DataObjectMapElement();
		response.put("indexName", indexName);
		response.put("displayName", struct.getDisplayName());
		response.put("description", struct.getDescription());
		response.put("status", searchMan.getIndexStatus(indexName));
		response.put("contentViewable", struct.contentViewable());
		String documentFormat = struct.getDocumentFormat();
		if (documentFormat != null && documentFormat.length() > 0)
			response.put("documentFormat", documentFormat);
		if (struct.getTokenCount() > 0)
			response.put("tokenCount", struct.getTokenCount());
		response.put("versionInfo", doVersionInfo);
		response.put("fieldInfo", doFieldInfo);

		// Remove any empty settings
		response.removeEmptyMapValues();

		Response r = new Response(response);
		r.setCacheAllowed(false); // because status might change (or you might reindex)
		return r;
	}

}

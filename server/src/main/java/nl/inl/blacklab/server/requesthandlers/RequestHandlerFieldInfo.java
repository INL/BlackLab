package nl.inl.blacklab.server.requesthandlers;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.indexstructure.ComplexFieldDesc;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
import nl.inl.blacklab.search.indexstructure.MetadataFieldDesc;
import nl.inl.blacklab.search.indexstructure.PropertyDesc;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.dataobject.DataObjectMapAttribute;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.util.StringUtil;

/**
 * Get information about the structure of an index.
 */
public class RequestHandlerFieldInfo extends RequestHandler {

	public RequestHandlerFieldInfo(BlackLabServer servlet, HttpServletRequest request, User user, String indexName, String urlResource, String urlPathPart) {
		super(servlet, request, user, indexName, urlResource, urlPathPart);
	}

	@Override
	public Response handle() throws BlsException {

		int i = urlPathInfo.indexOf('/');
		String fieldName = i >= 0 ? urlPathInfo.substring(0, i) : urlPathInfo;
		if (fieldName.length() == 0) {
			// FIXME show list of fields?
			throw new BadRequest("NO_DOC_ID", "Specify document pid.");
		}

		Searcher searcher = getSearcher();
		IndexStructure struct = searcher.getIndexStructure();

		DataObjectMapElement response = new DataObjectMapElement();
		if (struct.getComplexFields().contains(fieldName)) {
			ComplexFieldDesc fieldDesc = struct.getComplexFieldDesc(fieldName);
			response.put("indexName", indexName);
			response.put("fieldName", fieldName);
			response.put("isComplexField", "true");
			response.put("displayName", fieldDesc.getDisplayName());
			response.put("description", fieldDesc.getDescription());
			response.put("hasContentStore", fieldDesc.hasContentStore());
			response.put("hasXmlTags", fieldDesc.hasXmlTags());
			response.put("hasLengthTokens", fieldDesc.hasLengthTokens());
			response.put("mainProperty", fieldDesc.getMainProperty().getName());
			DataObjectMapAttribute doProps = new DataObjectMapAttribute("property", "name");
			for (String propName: fieldDesc.getProperties()) {
				PropertyDesc propDesc = fieldDesc.getPropertyDesc(propName);
				DataObjectMapElement doProp = new DataObjectMapElement();
				doProp.put("hasForwardIndex", propDesc.hasForwardIndex());
				doProp.put("sensitivity", propDesc.getSensitivity().toString());
				doProp.put("offsetsAlternative", StringUtil.nullToEmpty(propDesc.offsetsAlternative()));
				doProps.put(propName, doProp);
			}
			response.put("properties", doProps);
		} else {
			MetadataFieldDesc fd = struct.getMetadataFieldDesc(fieldName);
			Map<String, Integer> values = fd.getValueDistribution();
			boolean valueListComplete = fd.isValueListComplete();

			// Assemble response
			DataObjectMapAttribute doFieldValues = new DataObjectMapAttribute("value", "text");
			for (Map.Entry<String, Integer> e: values.entrySet()) {
				doFieldValues.put(e.getKey(), e.getValue());
			}
			response.put("indexName", indexName);
			response.put("fieldName", fieldName);
			response.put("isComplexField", "false");
			response.put("displayName", fd.getDisplayName());
			response.put("description", fd.getDescription());
			response.put("group", fd.getGroup());
			response.put("type", fd.getType().toString());
			response.put("analyzer", fd.getAnalyzerName());
			response.put("unknownCondition", fd.getUnknownCondition().toString());
			response.put("unknownValue", fd.getUnknownValue());
			response.put("fieldValues", doFieldValues);
			response.put("valueListComplete", valueListComplete);
		}

		// Remove any empty settings
		response.removeEmptyMapValues();

		Response responseObj = new Response(response);
		responseObj.setCacheAllowed(false); // Because reindexing might change something
		return responseObj;
	}

}

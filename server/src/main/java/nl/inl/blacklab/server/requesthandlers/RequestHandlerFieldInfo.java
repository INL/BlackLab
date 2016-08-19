package nl.inl.blacklab.server.requesthandlers;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.indexstructure.ComplexFieldDesc;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
import nl.inl.blacklab.search.indexstructure.MetadataFieldDesc;
import nl.inl.blacklab.search.indexstructure.PropertyDesc;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
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
	public boolean isCacheAllowed() {
		return false; // Because reindexing might change something
	}

	@Override
	public int handle(DataStream ds) throws BlsException {

		int i = urlPathInfo.indexOf('/');
		String fieldName = i >= 0 ? urlPathInfo.substring(0, i) : urlPathInfo;
		if (fieldName.length() == 0) {
			// FIXME show list of fields?
			throw new BadRequest("NO_DOC_ID", "Specify document pid.");
		}

		Searcher searcher = getSearcher();
		IndexStructure struct = searcher.getIndexStructure();

		ds.startMap();
		if (struct.getComplexFields().contains(fieldName)) {
			ComplexFieldDesc fieldDesc = struct.getComplexFieldDesc(fieldName);
			ds	.entry("indexName", indexName)
				.entry("fieldName", fieldName)
				.entry("isComplexField", "true")
				.entry("displayName", fieldDesc.getDisplayName())
				.entry("description", fieldDesc.getDescription())
				.entry("hasContentStore", fieldDesc.hasContentStore())
				.entry("hasXmlTags", fieldDesc.hasXmlTags())
				.entry("hasLengthTokens", fieldDesc.hasLengthTokens())
				.entry("mainProperty", fieldDesc.getMainProperty().getName());
			ds.startEntry("properties").startMap();
			for (String propName: fieldDesc.getProperties()) {
				PropertyDesc propDesc = fieldDesc.getPropertyDesc(propName);
				ds.startAttrEntry("property", "name", propName)
					.startMap()
						.entry("hasForwardIndex", propDesc.hasForwardIndex())
						.entry("sensitivity", propDesc.getSensitivity().toString())
						.entry("offsetsAlternative", StringUtil.nullToEmpty(propDesc.offsetsAlternative()))
					.endMap()
				.endAttrEntry();
			}
			ds.endMap().endEntry();
		} else {
			MetadataFieldDesc fd = struct.getMetadataFieldDesc(fieldName);
			Map<String, Integer> values = fd.getValueDistribution();
			boolean valueListComplete = fd.isValueListComplete();

			// Assemble response
			ds	.entry("indexName", indexName)
				.entry("fieldName", fieldName)
				.entry("isComplexField", "false")
				.entry("displayName", fd.getDisplayName())
				.entry("description", fd.getDescription())
				.entry("group", fd.getGroup())
				.entry("type", fd.getType().toString())
				.entry("analyzer", fd.getAnalyzerName())
				.entry("unknownCondition", fd.getUnknownCondition().toString())
				.entry("unknownValue", fd.getUnknownValue());
			ds.startEntry("fieldValues").startMap();
			for (Map.Entry<String, Integer> e: values.entrySet()) {
				ds.attrEntry("value", "text", e.getKey(), e.getValue());
			}
			ds.endMap().endEntry()
				.entry("valueListComplete", valueListComplete);
		}
		ds.endMap();

		// Remove any empty settings
		//response.removeEmptyMapValues();

		return HTTP_OK;
	}

}

package nl.inl.blacklab.server.requesthandlers;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
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
import nl.inl.util.LuceneUtil;
import nl.inl.util.StringUtil;

/**
 * Get information about the structure of an index.
 */
public class RequestHandlerFieldInfo extends RequestHandler {

    final private int MAX_FIELD_VALUES = 500;

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
			throw new BadRequest("UNKNOWN_OPERATION", "Bad URL. Either specify a field name to show information about, or remove the 'fields' part to get general index information.");
		}

		Searcher searcher = getSearcher();
		IndexStructure struct = searcher.getIndexStructure();

		ds.startMap();
		if (struct.getComplexFields().contains(fieldName)) {
            String showValuesFor = searchParam.getString("listvalues");
            String showSubpropsFor = searchParam.getString("subprops");
            describeComplexField(ds, fieldName, struct, searcher, showValuesFor, showSubpropsFor);
		} else {
			describeMetadataField(ds, fieldName, struct);
		}
		ds.endMap();

		// Remove any empty settings
		//response.removeEmptyMapValues();

		return HTTP_OK;
	}

	private void describeMetadataField(DataStream ds, String fieldName, IndexStructure struct) {
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

	private void describeComplexField(DataStream ds, String fieldName, IndexStructure struct, Searcher searcher, String showValuesFor, String showSubPropsFor) {
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
		Set<String> setShowValues = new HashSet<>();
		if (showValuesFor != null && showValuesFor.length() > 0) {
		    setShowValues.addAll(Arrays.asList(showValuesFor.split(",")));
		}
		Set<String> setShowSubprops = new HashSet<>();
		if (showSubPropsFor != null && showSubPropsFor.length() > 0) {
			setShowSubprops.addAll(Arrays.asList(showSubPropsFor.split(",")));
		}
		for (String propName: fieldDesc.getProperties()) {
			PropertyDesc propDesc = fieldDesc.getPropertyDesc(propName);
			ds.startAttrEntry("property", "name", propName)
				.startMap()
                    .entry("displayName", propDesc.getDisplayName())
					.entry("hasForwardIndex", propDesc.hasForwardIndex())
					.entry("sensitivity", propDesc.getSensitivity().toString())
					.entry("offsetsAlternative", StringUtil.nullToEmpty(propDesc.offsetsAlternative()))
				.endMap();
            String luceneField = ComplexFieldUtil.propertyField(fieldName, propName, ComplexFieldUtil.INSENSITIVE_ALT_NAME);
			if (setShowValues.contains(propName)) {
                Collection<String> values = LuceneUtil.getFieldTerms(searcher.getIndexReader(), luceneField, MAX_FIELD_VALUES + 1);
			    ds.startEntry("values").startList();
			    int n = 0;
			    for (String value: values) {
			        if (!value.contains(ComplexFieldUtil.SUBPROPERTY_SEPARATOR))
			            ds.item("value", value);
			        n++;
			        if (n == MAX_FIELD_VALUES)
			            break;
			    }
			    ds.endList().endEntry();
		        ds.entry("valueListComplete", values.size() <= MAX_FIELD_VALUES);
			}
			if (setShowSubprops.contains(propName)) {
				Map<String, Set<String>> subprops = LuceneUtil.getSubprops(searcher.getIndexReader(), luceneField);
				ds.startEntry("subproperties").startMap();
				for (Map.Entry<String, Set<String>> subprop: subprops.entrySet()) {
					String name = subprop.getKey();
					Set<String> values = subprop.getValue();
					ds.startAttrEntry("subproperty", "name", name).startList();
					for (String value: values) {
						ds.item("value", value);
					}
					ds.endList().endAttrEntry();
				}
				ds.endMap().endEntry();
			}
			ds.endAttrEntry();
		}
		ds.endMap().endEntry();
	}

}

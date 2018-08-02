package nl.inl.blacklab.server.requesthandlers;

import java.text.Collator;
import java.text.ParseException;
import java.text.RuleBasedCollator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.indexstructure.ComplexFieldDesc;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
import nl.inl.blacklab.search.indexstructure.MetadataFieldDesc;
import nl.inl.blacklab.search.indexstructure.MetadataFieldDesc.ValueListComplete;
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

    private static final int MAX_FIELD_VALUES = 500;

    private static RuleBasedCollator valueSortCollator = null;

    /**
     * Returns a collator that sort values "properly", ignoring parentheses.
     *
     * @return the collator
     */
    static Collator getValueSortCollator() {
        if (valueSortCollator == null) {
            valueSortCollator = (RuleBasedCollator) Searcher.getDefaultCollator();
            try {
                // Make sure it ignores parentheses when comparing
                String rules = valueSortCollator.getRules();
                rules = rules.replace("<'('<')'", ""); // Remove old rules for parentheses
                rules = ", '(',')' " + rules; // Make parentheses ignorable characters
                valueSortCollator = new RuleBasedCollator(rules);
            } catch (ParseException e) {
                // Oh well, we'll use the collator as-is
                //throw new RuntimeException();//DEBUG
            }
        }
        return valueSortCollator;
    }

    public RequestHandlerFieldInfo(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
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
            throw new BadRequest("UNKNOWN_OPERATION",
                    "Bad URL. Either specify a field name to show information about, or remove the 'fields' part to get general index information.");
        }

        Searcher searcher = getSearcher();
        IndexStructure struct = searcher.getIndexStructure();

        if (struct.getComplexFields().contains(fieldName)) {
            Set<String> setShowValuesFor = searchParam.listValuesFor();
            Set<String> setShowSubpropsFor = searchParam.listSubpropsFor();
            ComplexFieldDesc fieldDesc = struct.getComplexFieldDesc(fieldName);
            describeComplexField(ds, indexName, fieldName, fieldDesc, searcher, setShowValuesFor, setShowSubpropsFor);
        } else {
            MetadataFieldDesc fieldDesc = struct.getMetadataFieldDesc(fieldName);
            describeMetadataField(ds, indexName, fieldName, fieldDesc, true);
        }

        // Remove any empty settings
        //response.removeEmptyMapValues();

        return HTTP_OK;
    }

    public static void describeMetadataField(DataStream ds, String indexName, String fieldName, MetadataFieldDesc fd,
            boolean listValues) {
        ds.startMap();
        boolean valueListComplete = fd.isValueListComplete().equals(ValueListComplete.YES); // report false for UNKNOWN - this usually means there's no values either way

        // Assemble response
        if (indexName != null)
            ds.entry("indexName", indexName);
        ds.entry("fieldName", fieldName)
                .entry("isComplexField", "false")
                .entry("displayName", fd.getDisplayName())
                .entry("description", fd.getDescription())
                .entry("uiType", fd.getUiType());
        String group = fd.getGroup();
        if (group != null && group.length() > 0)
            ds.entry("group", group);
        ds
                .entry("type", fd.getType().toString())
                .entry("analyzer", fd.getAnalyzerName())
                .entry("unknownCondition", fd.getUnknownCondition().toString())
                .entry("unknownValue", fd.getUnknownValue());
        if (listValues) {
            final Map<String, String> displayValues = fd.getDisplayValues();
            ds.startEntry("displayValues").startMap();
            for (Map.Entry<String, String> e : displayValues.entrySet()) {
                ds.attrEntry("displayValue", "value", e.getKey(), e.getValue());
            }
            ds.endMap().endEntry();

            // Show values in display order (if defined)
            // If not all values are mentioned in display order, show the rest at the end,
            // sorted by their displayValue (or regular value if no displayValue specified)
            ds.startEntry("fieldValues").startMap();
            Map<String, Integer> values = fd.getValueDistribution();
            Set<String> valuesLeft = new HashSet<>(values.keySet());
            for (String value : fd.getDisplayOrder()) {
                ds.attrEntry("value", "text", value, values.get(value));
                valuesLeft.remove(value);
            }
            List<String> sortedLeft = new ArrayList<>(valuesLeft);
            final Collator defaultCollator = getValueSortCollator();
            Collections.sort(sortedLeft, new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    String d1 = displayValues.containsKey(o1) ? displayValues.get(o1) : o1;
                    String d2 = displayValues.containsKey(o2) ? displayValues.get(o2) : o2;
                    //return d1.compareTo(d2);
                    return defaultCollator.compare(d1, d2);
                }
            });
            for (String value : sortedLeft) {
                ds.attrEntry("value", "text", value, values.get(value));
            }
            ds.endMap().endEntry()
                    .entry("valueListComplete", valueListComplete);
        }
        ds.endMap();
    }

    public static void describeComplexField(DataStream ds, String indexName, String fieldName,
            ComplexFieldDesc fieldDesc, Searcher searcher, Set<String> showValuesFor, Set<String> showSubpropsFor) {
        ds.startMap();
        if (indexName != null)
            ds.entry("indexName", indexName);
        ds.entry("fieldName", fieldName)
                .entry("isComplexField", "true")
                .entry("displayName", fieldDesc.getDisplayName())
                .entry("description", fieldDesc.getDescription())
                .entry("hasContentStore", fieldDesc.hasContentStore())
                .entry("hasXmlTags", fieldDesc.hasXmlTags())
                .entry("hasLengthTokens", fieldDesc.hasLengthTokens())
                .entry("mainProperty", fieldDesc.getMainProperty().getName());
        ds.startEntry("properties").startMap();
        List<String> properties = new ArrayList<>(fieldDesc.getProperties());
        for (String propName : properties) {
            PropertyDesc propDesc = fieldDesc.getPropertyDesc(propName);
            ds.startAttrEntry("property", "name", propName).startMap();
            ds
                    .entry("displayName", propDesc.getDisplayName())
                    .entry("description", propDesc.getDescription())
                    .entry("uiType", propDesc.getUiType())
                    .entry("hasForwardIndex", propDesc.hasForwardIndex())
                    .entry("sensitivity", propDesc.getSensitivity().toString())
                    .entry("offsetsAlternative", StringUtil.nullToEmpty(propDesc.offsetsAlternative()))
                    .entry("isInternal", propDesc.isInternal());
            String luceneField = ComplexFieldUtil.propertyField(fieldName, propName,
                    ComplexFieldUtil.INSENSITIVE_ALT_NAME);
            if (showValuesFor.contains(propName)) {
                Collection<String> values = LuceneUtil.getFieldTerms(searcher.getIndexReader(), luceneField,
                        MAX_FIELD_VALUES + 1);
                ds.startEntry("values").startList();
                int n = 0;
                for (String value : values) {
                    if (!value.contains(ComplexFieldUtil.SUBPROPERTY_SEPARATOR))
                        ds.item("value", value);
                    n++;
                    if (n == MAX_FIELD_VALUES)
                        break;
                }
                ds.endList().endEntry();
                ds.entry("valueListComplete", values.size() <= MAX_FIELD_VALUES);
            }
            if (showSubpropsFor.contains(propName)) {
                Map<String, Set<String>> subprops = LuceneUtil.getSubprops(searcher.getIndexReader(), luceneField);
                ds.startEntry("subproperties").startMap();
                for (Map.Entry<String, Set<String>> subprop : subprops.entrySet()) {
                    String name = subprop.getKey();
                    Set<String> values = subprop.getValue();
                    ds.startAttrEntry("subproperty", "name", name).startList();
                    for (String value : values) {
                        ds.item("value", value);
                    }
                    ds.endList().endAttrEntry();
                }
                ds.endMap().endEntry();
            }
            ds.endMap().endAttrEntry();
        }
        ds.endMap().endEntry();
        ds.endMap();
    }

}

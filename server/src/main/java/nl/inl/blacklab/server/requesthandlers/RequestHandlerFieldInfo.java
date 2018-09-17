package nl.inl.blacklab.server.requesthandlers;

import java.text.Collator;
import java.text.ParseException;
import java.text.RuleBasedCollator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexImpl;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.Annotations;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.ValueListComplete;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.util.LuceneUtil;

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
            valueSortCollator = (RuleBasedCollator) BlackLabIndexImpl.defaultCollator();
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

        BlackLabIndex blIndex = blIndex();
        IndexMetadata indexMetadata = blIndex.metadata();

        if (indexMetadata.annotatedFields().exists(fieldName)) {
            Set<String> setShowValuesFor = searchParam.listValuesFor();
            Set<String> setShowSubpropsFor = searchParam.listSubpropsFor();
            AnnotatedField fieldDesc = indexMetadata.annotatedField(fieldName);
            describeAnnotatedField(ds, indexName, fieldDesc, blIndex, setShowValuesFor, setShowSubpropsFor);
        } else {
            MetadataField fieldDesc = indexMetadata.metadataField(fieldName);
            describeMetadataField(ds, indexName, fieldDesc, true);
        }

        // Remove any empty settings
        //response.removeEmptyMapValues();

        return HTTP_OK;
    }

    public static void describeMetadataField(DataStream ds, String indexName, MetadataField fd, boolean listValues) {
        ds.startMap();
        // (we report false for ValueListComplete.UNKNOWN - this usually means there's no values either way)
        boolean valueListComplete = fd.isValueListComplete().equals(ValueListComplete.YES); 

        // Assemble response
        if (indexName != null)
            ds.entry("indexName", indexName);
        ds.entry("fieldName", fd.name())
                .entry(ElementNames.isAnnotatedField, "false")
                .entry("displayName", fd.displayName())
                .entry("description", fd.description())
                .entry("uiType", fd.uiType());
        String group = fd.group();
        if (group != null && group.length() > 0)
            ds.entry("group", group);
        ds
                .entry("type", fd.type().toString())
                .entry("analyzer", fd.analyzerName())
                .entry("unknownCondition", fd.unknownCondition().toString())
                .entry("unknownValue", fd.unknownValue());
        if (listValues) {
            final Map<String, String> displayValues = fd.displayValues();
            ds.startEntry("displayValues").startMap();
            for (Map.Entry<String, String> e : displayValues.entrySet()) {
                ds.attrEntry("displayValue", "value", e.getKey(), e.getValue());
            }
            ds.endMap().endEntry();

            // Show values in display order (if defined)
            // If not all values are mentioned in display order, show the rest at the end,
            // sorted by their displayValue (or regular value if no displayValue specified)
            ds.startEntry("fieldValues").startMap();
            Map<String, Integer> values = fd.valueDistribution();
            Set<String> valuesLeft = new HashSet<>(values.keySet());
            for (String value : fd.displayOrder()) {
                ds.attrEntry("value", "text", value, values.get(value));
                valuesLeft.remove(value);
            }
            List<String> sortedLeft = new ArrayList<>(valuesLeft);
            final Collator defaultCollator = getValueSortCollator();
            sortedLeft.sort(new Comparator<String>() {
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
    
    public static String sensitivitySettingDesc(Annotation annotation) {
        String sensitivityDesc;
        if (annotation.hasSensitivity(MatchSensitivity.SENSITIVE)) {
            if (annotation.hasSensitivity(MatchSensitivity.INSENSITIVE)) {
                if (annotation.hasSensitivity(MatchSensitivity.CASE_INSENSITIVE)) {
                    sensitivityDesc = "CASE_AND_DIACRITICS_SEPARATE";
                } else {
                    sensitivityDesc = "SENSITIVE_AND_INSENSITIVE";
                }
            } else {
                sensitivityDesc = "ONLY_SENSITIVE";
            }
        } else {
            sensitivityDesc = "ONLY_INSENSITIVE";
        }
        return sensitivityDesc;
    }

    public static void describeAnnotatedField(DataStream ds, String indexName, 
            AnnotatedField fieldDesc, BlackLabIndex index, Set<String> showValuesFor, Set<String> showSubpropsFor) {
        ds.startMap();
        if (indexName != null)
            ds.entry("indexName", indexName);
        Annotations annotations = fieldDesc.annotations();
        ds.entry("fieldName", fieldDesc.name())
                .entry(ElementNames.isAnnotatedField, "true")
                .entry("displayName", fieldDesc.displayName())
                .entry("description", fieldDesc.description())
                .entry("hasContentStore", fieldDesc.hasContentStore())
                .entry("hasXmlTags", fieldDesc.hasXmlTags())
                .entry("hasLengthTokens", fieldDesc.hasLengthTokens())
                .entry("mainProperty", annotations.main().name());
        ds.startEntry(ElementNames.annotations).startMap();
        for (Annotation annotation: annotations) {
            ds.startAttrEntry(ElementNames.annotation, "name", annotation.name()).startMap();
            AnnotationSensitivity offsetsSensitivity = annotation.offsetsSensitivity();
            String offsetsAlternative = offsetsSensitivity == null ? "" : offsetsSensitivity.sensitivity().luceneFieldSuffix();
            ds
                    .entry("displayName", annotation.displayName())
                    .entry("description", annotation.description())
                    .entry("uiType", annotation.uiType())
                    .entry("hasForwardIndex", annotation.hasForwardIndex())
                    .entry("sensitivity", sensitivitySettingDesc(annotation))
                    .entry("offsetsAlternative", offsetsAlternative)
                    .entry("isInternal", annotation.isInternal());
            AnnotationSensitivity as = annotation.sensitivity(annotation.hasSensitivity(MatchSensitivity.INSENSITIVE) ? MatchSensitivity.INSENSITIVE : MatchSensitivity.SENSITIVE);
            String luceneField = as.luceneField();
            if (showValuesFor.contains(annotation.name())) {
                Collection<String> values = LuceneUtil.getFieldTerms(index.reader(), luceneField,
                        MAX_FIELD_VALUES + 1);
                ds.startEntry("values").startList();
                int n = 0;
                for (String value : values) {
                    if (!value.contains(AnnotatedFieldNameUtil.SUBANNOTATION_SEPARATOR))
                        ds.item("value", value);
                    n++;
                    if (n == MAX_FIELD_VALUES)
                        break;
                }
                ds.endList().endEntry();
                ds.entry("valueListComplete", values.size() <= MAX_FIELD_VALUES);
            }
            if (showSubpropsFor.contains(annotation.name())) {
                Map<String, Set<String>> subprops = LuceneUtil.getSubprops(index.reader(), luceneField);
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

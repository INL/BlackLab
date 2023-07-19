package nl.inl.blacklab.search.textpattern;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A TextPattern matching a word.
 */
public class TextPatternTags extends TextPattern {

    protected final String elementName;

    final Map<String, String> attr;

    private final String captureAs;

    public TextPatternTags(String elementName, Map<String, String> attr, String captureAs) {
        this.elementName = elementName;
        this.attr = attr == null ? Collections.emptyMap() : attr;
        this.captureAs = captureAs == null ? "" : captureAs;
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) {
        // Desensitize tag name and attribute values if required
        context = context.withRelationAnnotation();
        String elementName1 = optInsensitive(context, elementName);
        Map<String, String> attrOptIns = new HashMap<>();
        for (Map.Entry<String, String> e : attr.entrySet()) {
            attrOptIns.put(e.getKey(), optInsensitive(context, e.getValue()));
        }

        // Return the proper SpanQuery depending on index version
        QueryInfo queryInfo = QueryInfo.create(context.index(), context.field());
        return context.index().tagQuery(queryInfo, context.luceneField(), elementName1, attrOptIns, captureAs);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternTags) {
            TextPatternTags tp = ((TextPatternTags) obj);
            return elementName.equals(tp.elementName) && attr.equals(tp.attr);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return elementName.hashCode() + attr.hashCode();
    }

    @Override
    public String toString() {
        if (attr != null && !attr.isEmpty())
            return "TAGS(" + elementName + ", " + attr + ")";
        return "TAGS(" + elementName + ")";
    }

    public TextPatternTags withCapture(String groupName) {
        return new TextPatternTags(elementName, attr, groupName);
    }
}

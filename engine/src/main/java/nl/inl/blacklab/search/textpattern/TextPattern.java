package nl.inl.blacklab.search.textpattern;

import java.util.List;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.exceptions.RegexpTooLarge;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.extensions.XFRelations;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryFiltered;
import nl.inl.blacklab.search.lucene.SpanQueryPositionFilter;
import nl.inl.blacklab.search.matchfilter.TextPatternStruct;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Describes some pattern of words in a content field. The point of this
 * interface is to provide an abstract layer to describe the pattern we're
 * interested in, which can then be translated into, for example, a SpanQuery
 * object or a String, depending on our needs.
 */
public abstract class TextPattern implements TextPatternStruct {

    /** Value meaning "no maximum" (actually just the largest integer) */
    public static final int MAX_UNLIMITED = BLSpanQuery.MAX_UNLIMITED;

    // Node types
    public static final String NT_AND = "and";
    public static final String NT_ANDNOT = "andnot";
    public static final String NT_ANNOTATION = "annotation";
    public static final String NT_ANYTOKEN = "anytoken";
    public static final String NT_CAPTURE = "capture";
    public static final String NT_CONSTRAINED = "constrained";
    public static final String NT_DEFVAL = "defval";
    public static final String NT_EDGE = "edge";
    public static final String NT_EXPANSION = "expansion";
    public static final String NT_FILTERNGRAMS = "filterngrams";
    public static final String NT_FIXEDSPAN = "fixedspan";
    public static final String NT_FUZZY = "fuzzy";
    public static final String NT_INT_RANGE = "intrange";
    public static final String NT_NOT = "not";
    public static final String NT_OR = "or";
    public static final String NT_POSFILTER = "posfilter";
    public static final String NT_OVERLAPPING = "overlapping";
    public static final String NT_PREFIX = "prefix";
    public static final String NT_CALLFUNC = "callfunc";
    public static final String NT_REGEX = "regex";
    public static final String NT_RELATION_MATCH = "relmatch";
    public static final String NT_RELATION_TARGET = "reltarget";
    public static final String NT_REPEAT = "repeat";
    public static final String NT_SENSITIVITY = "sensitivity";
    public static final String NT_SEQUENCE = "sequence";
    public static final String NT_SETTINGS = "settings";
    public static final String NT_TAGS = "tags";
    public static final String NT_TERM = "term";
    public static final String NT_WILDCARD = "wildcard";

    /**
     * Make sure the query is within the specified tag, and capture relations within the tag.
     *
     * E.g. you want hits inside sentences, and want to capture all (dependency) relations
     * in that sentence.
     *
     * Essentially adds <code>within rcapture(<s/>)</code> to the query if <code>tagName == "s"</code>.
     *
     * @param pattern pattern to filter
     * @param tagName tag the hits must be within
     * @return the filtered pattern, where relations within the tag will be captured
     */
    public static TextPatternPositionFilter createRelationCapturingWithinQuery(TextPattern pattern, String tagName, String captureRelsAs) {
        TextPattern tags = new TextPatternTags(tagName, null, TextPatternTags.Adjust.FULL_TAG, tagName);
        // Also capture any relations that are in the tag
        tags = new TextPatternQueryFunction(XFRelations.FUNC_RCAPTURE, List.of(tags, captureRelsAs));
        return new TextPatternPositionFilter(pattern, tags, SpanQueryPositionFilter.Operation.WITHIN);
    }

    /**
     * Translate this TextPattern into a BLSpanQuery.
     *
     * @param context query execution context to use
     * @return result of the translation
     * @throws RegexpTooLarge if a regular expression was too large
     * @throws InvalidQuery if something else was wrong about the query (e.g. error in regex expression)
     */
    public abstract BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery;

    @Override
    public abstract String toString();

    protected String clausesToString(List<TextPattern> clauses) {
        StringBuilder b = new StringBuilder();
        for (TextPattern clause : clauses) {
            if (b.length() > 0)
                b.append(", ");
            b.append(clause.toString());
        }
        return b.toString();
    }

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();

    String optInsensitive(QueryExecutionContext context, String value) {
        return context.optDesensitize(value);
    }

    public BLSpanQuery toQuery(QueryInfo queryInfo) throws InvalidQuery {
        return toQuery(queryInfo, null);
    }

    public BLSpanQuery toQuery(QueryInfo queryInfo, Query filter) throws InvalidQuery {
        QueryExecutionContext context = queryInfo.index().defaultExecutionContext(queryInfo.field());
        BLSpanQuery spanQuery = translate(context);
        if (filter != null)
            spanQuery = new SpanQueryFiltered(spanQuery, filter);
        return spanQuery;
    }

    /** Does this query involve any (e.g. dependency) relations operations?
     *
     * (we sometimes treat these queries slightly differently, e.g. adjusting hits to encompass
     *  matched relations, if requested)
     */
    public boolean isRelationsQuery() {
        return false;
    }
}

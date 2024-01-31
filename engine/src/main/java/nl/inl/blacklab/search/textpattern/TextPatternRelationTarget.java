package nl.inl.blacklab.search.textpattern;

import java.util.Objects;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.extensions.XFRelations;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.SpanQueryNot;
import nl.inl.blacklab.search.lucene.SpanQueryRelations;

/**
 * Relations operator, matching a source (parent) to one or more targets (children).
 */
public class TextPatternRelationTarget extends TextPattern {

    private final String regex;

    private final boolean negate;

    private final TextPattern target;

    private final RelationInfo.SpanMode spanMode;

    private final SpanQueryRelations.Direction direction;

    private final String captureAs;

    public TextPatternRelationTarget(String regex, boolean negate, TextPattern target, RelationInfo.SpanMode spanMode,
            SpanQueryRelations.Direction direction, String captureAs) {
        this.regex = regex;
        this.negate = negate;
        this.target = target;
        this.spanMode = spanMode;
        this.direction = direction;
        this.captureAs = captureAs == null ? "" : captureAs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TextPatternRelationTarget that = (TextPatternRelationTarget) o;
        return negate == that.negate && Objects.equals(regex, that.regex) && Objects.equals(target,
                that.target) && spanMode == that.spanMode && direction == that.direction && Objects.equals(
                captureAs, that.captureAs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(regex, negate, target, spanMode, direction, captureAs);
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        // replace _ with any ngram
        TextPattern targetNoDefVal = TextPatternDefaultValue.replaceWithAnyToken(target);
        BLSpanQuery translated = XFRelations.createRelationQuery(
                context.queryInfo(),
                context,
                regex,
                targetNoDefVal.translate(context),
                direction,
                captureAs,
                spanMode
        );
        if (negate)
            translated = new SpanQueryNot(translated);
        return translated;
    }

    @Override
    public String toString() {
        String optCapture = captureAs.isEmpty() ? "" : ", " + captureAs;
        String optNegate = negate ? ", NEGATE" : "";
        return "REL(" + regex + optNegate + ", " + target + optCapture + ")";
    }

    public String getRegex() {
        return regex;
    }

    public TextPattern getTarget() {
        return target;
    }

    public String getCaptureAs() {
        return captureAs;
    }

    public boolean isNegate() {
        return negate;
    }

    public RelationInfo.SpanMode getSpanMode() {
        return spanMode;
    }

    public SpanQueryRelations.Direction getDirection() {
        return direction;
    }

    @Override
    public boolean isRelationsQuery() {
        return true;
    }
}
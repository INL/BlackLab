package nl.inl.blacklab.search.textpattern;

import java.util.Objects;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.extensions.XFRelations;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.SpanQueryNot;
import nl.inl.blacklab.search.matchfilter.TextPatternStruct;

/**
 * Relations operator plus an optional target clause.
 */
public class RelationTarget implements TextPatternStruct {

    /** How to find relations (type filter, negation, etc.) */
    private final RelationOperatorInfo opInfo;

    /** What the relation target must match */
    private final TextPattern target;

    /** What span to return from this: source, target or full span. */
    private final RelationInfo.SpanMode spanMode;

    /** How to capture the relation that was matched. */
    private final String captureAs;

    public RelationTarget(RelationOperatorInfo relOpInfo, TextPattern target, RelationInfo.SpanMode spanMode,
            String captureAs) {
        this.opInfo = relOpInfo;
        this.target = target;
        this.spanMode = spanMode;
        this.captureAs = captureAs == null ? "" : captureAs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        RelationTarget that = (RelationTarget) o;
        return Objects.equals(opInfo, that.opInfo) && Objects.equals(target, that.target)
                && spanMode == that.spanMode && Objects.equals(captureAs,
                that.captureAs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(opInfo, target, spanMode, captureAs);
    }

    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        // replace _ with any ngram
        TextPattern targetNoDefVal = TextPatternDefaultValue.replaceWithAnyToken(target);
        QueryExecutionContext targetContext = context.withDocVersion(opInfo.getTargetVersion());
        BLSpanQuery translated = XFRelations.createRelationQuery(
                context.queryInfo(),
                context,
                opInfo.getFullTypeRegex(),
                targetNoDefVal.translate(targetContext),
                opInfo.getDirection(),
                captureAs,
                spanMode,
                targetContext.luceneField()
        );
        if (opInfo.isNegate())
            translated = new SpanQueryNot(translated);
        return translated;
    }

    @Override
    public String toString() {
        String optCapture = captureAs.isEmpty() ? "" : ", " + captureAs;
        String optNegate = opInfo.isNegate() ? ", NEGATE" : "";
        return "REL(" + opInfo.getTypeRegex() + optNegate + ", " + target + optCapture + ")";
    }

    public RelationOperatorInfo getOperatorInfo() {
        return opInfo;
    }

    public TextPattern getTarget() {
        return target;
    }

    public String getCaptureAs() {
        return captureAs;
    }

    public RelationInfo.SpanMode getSpanMode() {
        return spanMode;
    }
}

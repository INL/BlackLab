package nl.inl.blacklab.search.textpattern;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.SpanQueryPositionFilter;
import nl.inl.blacklab.search.lucene.SpanQueryRelations;
import nl.inl.blacklab.search.matchfilter.MatchFilterAnd;
import nl.inl.blacklab.search.matchfilter.MatchFilterCompare;
import nl.inl.blacklab.search.matchfilter.MatchFilterEquals;
import nl.inl.blacklab.search.matchfilter.MatchFilterFunctionCall;
import nl.inl.blacklab.search.matchfilter.MatchFilterImplication;
import nl.inl.blacklab.search.matchfilter.MatchFilterNot;
import nl.inl.blacklab.search.matchfilter.MatchFilterOr;
import nl.inl.blacklab.search.matchfilter.MatchFilterSameTokens;
import nl.inl.blacklab.search.matchfilter.MatchFilterString;
import nl.inl.blacklab.search.matchfilter.MatchFilterTokenAnnotation;
import nl.inl.blacklab.search.matchfilter.MatchFilterTokenAnnotationEqualsString;
import nl.inl.blacklab.search.matchfilter.TextPatternStruct;
import nl.inl.util.StringUtil;

/**
 * Serialize a TextPattern (back) to a CorpusQL query.
 */
public class TextPatternSerializerCql {

    public static String serialize(TextPatternStruct pattern) {
        StringBuilder b = new StringBuilder();
        serialize(pattern, b, false, false);
        return b.toString();
    }

    public static void serialize(TextPatternStruct pattern, StringBuilder b) {
        serialize(pattern, b, false, false);
    }

    public static void serialize(TextPatternStruct pattern, StringBuilder b, boolean parenthesizeIfNecessary,
            boolean insideTokenBrackets) {
        NodeSerializer nodeSerializer = cqlSerializers.get(pattern.getClass());
        if (nodeSerializer == null)
            throw new UnsupportedOperationException("Cannot serialize " + pattern.getClass().getSimpleName() + " to CQL");
        nodeSerializer.serialize(pattern, b, parenthesizeIfNecessary, insideTokenBrackets);
    }

    private static void serializeRegexOrTerm(TextPatternStruct pattern, StringBuilder b, boolean parenthesizeIfNecessary,
            boolean insideTokenBrackets) {
        handleRegexOrTerm(pattern, b, insideTokenBrackets, false);
    }

    private static void handleRegexOrTerm(TextPatternStruct pattern, StringBuilder b, boolean insideTokenBrackets,
            boolean negate) {
        String className = pattern.getClass().getSimpleName();
        boolean isRegexPattern = pattern instanceof TextPatternRegex;
        TextPatternTerm tp = (TextPatternTerm) pattern;
        String annotation = tp.getAnnotation();
        if (negate && annotation == null)
            throw new UnsupportedOperationException("Cannot serialize negated " + className + " without annotation to CQL");
        MatchSensitivity sensitivity = tp.getSensitivity();
        if (sensitivity != null)
            throw new UnsupportedOperationException("Cannot serialize " + className + " with sensitivity to CQL");
        String optOpenBracket = insideTokenBrackets ? "" : "[";
        String optCloseBracket = insideTokenBrackets ? "" : "]";
        if (annotation != null)
            b.append(optOpenBracket).append(annotation).append(negate ? "!" : "").append("=");
        String value = tp.getValue();
        if (!isRegexPattern) {
            // We're looking for an exact value, which may include regex characters.
            value = StringUtil.escapeLuceneRegexCharacters(value);
        }
        serializeQuotedString(b, value);
        if (annotation != null)
            b.append(optCloseBracket);
    }

    interface NodeSerializer {
        void serialize(TextPatternStruct pattern, StringBuilder b, boolean parenthesizeIfNecessary,
                boolean insideTokenBrackets);
    }

    private static final Map<Class<? extends TextPatternStruct>, NodeSerializer> cqlSerializers = new LinkedHashMap<>();

    static {
        // For each node type, add a CQL serializer to the map.

        // AND
        cqlSerializers.put(TextPatternAnd.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            serializeOptBrackets(pattern, b, parenthesizeIfNecessary, insideTokenBrackets,
                    (parenthesize, brackets) -> {
                TextPatternAnd tp = (TextPatternAnd) pattern;
                infix(b, parenthesize, brackets, " & ", tp.getClauses());
            });
        });

        // ANDNOT
        //noinspection deprecation
//        cqlSerializers.put(TextPatternAndNot.class, TextPatternSerializerCql::serializeAndNot);

        // ANNOTATION
//        cqlSerializers.put(TextPatternAnnotation.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
//            throw new UnsupportedOperationException("Cannot serialize deprecated TextPatternAnnotation to CQL");
//        });

        // ANYTOKEN
        cqlSerializers.put(TextPatternAnyToken.class, (pattern1, b1, parenthesizeIfNecessary, insideTokenBrackets) -> {
            TextPatternAnyToken tp = (TextPatternAnyToken) pattern1;
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize TextPatternAnyToken inside brackets to CQL");
            b1.append("[]").append(repetitionOperator(tp.getMin(), tp.getMax()));
        });

        // CAPTURE
        cqlSerializers.put(TextPatternCaptureGroup.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize capture inside brackets to CQL");
            if (parenthesizeIfNecessary)
                b.append("(");
            TextPatternCaptureGroup tp = (TextPatternCaptureGroup) pattern;
            b.append(tp.getCaptureName()).append(":");
            serialize(tp.getClause(), b, true, insideTokenBrackets);
            if (parenthesizeIfNecessary)
                b.append(")");
        });

        // CONSTRAINED
        cqlSerializers.put(TextPatternConstrained.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize TextPatternConstrained inside brackets to CQL");
            TextPatternConstrained tp = (TextPatternConstrained) pattern;
            infix(b, parenthesizeIfNecessary, insideTokenBrackets, " :: ", List.of(tp.getClause(), tp.getConstraint()));
        });

        // DEFAULT VALUE
        cqlSerializers.put(TextPatternDefaultValue.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize TextPatternDefaultValue inside brackets to CQL");
            b.append("_");
        });

        // EDGE
//        cqlSerializers.put(TextPatternEdge.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
//            throw new UnsupportedOperationException("Cannot serialize TextPatternEdge to CQL");
//        });

        // EXPANSION
        cqlSerializers.put(TextPatternExpansion.class, TextPatternSerializerCql::serializeExpansion);

        // NOT
        cqlSerializers.put(TextPatternNot.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            serializeOptBrackets(pattern, b, parenthesizeIfNecessary, insideTokenBrackets,
            (parenthesize, brackets) -> {
                TextPatternNot tp = (TextPatternNot) pattern;
                if (tp.getClause() instanceof TextPatternTerm && brackets) {
                    handleRegexOrTerm(tp.getClause(), b, true, true);
                } else {
                    b.append("!");
                    serialize(tp.getClause(), b, true, brackets);
                }
            });
        });

        // OR
        cqlSerializers.put(TextPatternOr.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            serializeOptBrackets(pattern, b, parenthesizeIfNecessary, insideTokenBrackets,
                    (parenthesize, brackets) -> {
                TextPatternOr tp = (TextPatternOr) pattern;
                infix(b, parenthesize, brackets, " | ", tp.getClauses());
            });
        });

        // POSFILTER
        cqlSerializers.put(TextPatternPositionFilter.class, TextPatternSerializerCql::serializePosFilter);

        // PREFIX
//        cqlSerializers.put(TextPatternPrefix.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
//            throw new UnsupportedOperationException("Cannot serialize deprecated TextPatternPrefix to CQL (use regex)");
//        });

        // QUERYFUNCTION
        cqlSerializers.put(TextPatternQueryFunction.class, TextPatternSerializerCql::serializeFuncCall);

        // REGEX
        cqlSerializers.put(TextPatternRegex.class, TextPatternSerializerCql::serializeRegexOrTerm);

        // Relation match (parent + children)
        cqlSerializers.put(TextPatternRelationMatch.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize TextPatternRelationMatch inside brackets to CQL");
            TextPatternRelationMatch tp = (TextPatternRelationMatch) pattern;
            if (parenthesizeIfNecessary)
                b.append("(");
            serialize(tp.getParent(), b, true, insideTokenBrackets);
            boolean first = true;
            for (TextPattern child: tp.getChildren()) {
                if (!first)
                    b.append(" ;");
                first = false;
                serialize(child, b, true, insideTokenBrackets);
            }
            if (parenthesizeIfNecessary)
                b.append(")");
        });

        // Relation target (child)
        cqlSerializers.put(TextPatternRelationTarget.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize TextPatternRelationTarget inside brackets to CQL");
            TextPatternRelationTarget tp = (TextPatternRelationTarget) pattern;
            String optCapture = tp.getCaptureAs().isEmpty() ? "" : tp.getCaptureAs() + ":";
            String optRegex = tp.getRegex().equals(".*") ? "" : tp.getRegex();
            boolean isRoot = tp.getDirection() == SpanQueryRelations.Direction.ROOT;
            if (isRoot && tp.getSpanMode() != RelationInfo.SpanMode.TARGET)
                throw new IllegalArgumentException("Root relation must have span mode target (has no source)");
            String optOperatorPrefix = isRoot ? "^" : (tp.isNegate() ? "!" : "");
            b.append(isRoot ? "" : " ").append(optCapture).append(optOperatorPrefix).append("-").append(optRegex).append("-> ");
            serialize(tp.getTarget(), b, true, insideTokenBrackets);
        });

        // REPETITION
        cqlSerializers.put(TextPatternRepetition.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize TextPatternRepetition inside brackets to CQL");
            TextPatternRepetition tp = (TextPatternRepetition) pattern;
            if (parenthesizeIfNecessary)
                b.append("(");
            serialize(tp.getClause(), b, true, insideTokenBrackets);
            b.append(repetitionOperator(tp.getMin(), tp.getMax()));
            if (parenthesizeIfNecessary)
                b.append(")");
        });

        // SENSITIVE
//        cqlSerializers.put(TextPatternSensitive.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
//            throw new UnsupportedOperationException("Cannot serialize deprecated TextPatternSensitive to CQL");
//        });

        // SEQUENCE
        cqlSerializers.put(TextPatternSequence.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize TextPatternSequence inside brackets to CQL");
            infix(b, parenthesizeIfNecessary, insideTokenBrackets, " ", ((TextPatternSequence)pattern).getClauses());
        });

        // TAGS
        cqlSerializers.put(TextPatternTags.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            if (insideTokenBrackets)
                throw new UnsupportedOperationException("Cannot serialize TextPatternTags inside brackets to CQL");
            TextPatternTags tp = (TextPatternTags) pattern;
            String optAttr = tp.getAttributes().isEmpty() ? "" : " " + serializeAttributes(tp.getAttributes());
            String optCapture = tp.getCaptureAs().isEmpty() ? "" : tp.getCaptureAs() + ":";
            b.append(optCapture).append("<").append(tp.getElementName()).append(optAttr).append("/>");
        });

        // TERM
        cqlSerializers.put(TextPatternTerm.class, TextPatternSerializerCql::serializeRegexOrTerm);

        // WILDCARD
//        cqlSerializers.put(TextPatternWildcard.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
//            throw new UnsupportedOperationException("Cannot serialize deprecated TextPatternWildcard to CQL (use regex)");
//        });

        // MatchFilter AND
        cqlSerializers.put(MatchFilterAnd.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            infix(b, parenthesizeIfNecessary, insideTokenBrackets, " & ", ((MatchFilterAnd) pattern).getClauses());
        });

        // MatchFilter compare
        cqlSerializers.put(MatchFilterCompare.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            MatchFilterCompare tp = (MatchFilterCompare) pattern;
            infix(b, parenthesizeIfNecessary, insideTokenBrackets, " " + tp.getOperator() + " ", tp.getClauses());
        });

        // MatchFilter equals
        cqlSerializers.put(MatchFilterEquals.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            infix(b, parenthesizeIfNecessary, insideTokenBrackets, " = ", ((MatchFilterEquals) pattern).getClauses());
        });

        // MatchFilter funccall
        cqlSerializers.put(MatchFilterFunctionCall.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            MatchFilterFunctionCall tp = (MatchFilterFunctionCall) pattern;
            b.append(tp.getName()).append("(" + tp.getCapture() + ")");
        });

        // MatchFilter implication
        cqlSerializers.put(MatchFilterImplication.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            MatchFilterImplication tp = (MatchFilterImplication) pattern;
            infix(b, parenthesizeIfNecessary, insideTokenBrackets, " -> ", tp.getClauses());
        });

        // MatchFilter NOT
        cqlSerializers.put(MatchFilterNot.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            b.append("!");
            serialize(((MatchFilterNot)pattern).getClause(), b, parenthesizeIfNecessary, insideTokenBrackets);
        });

        // MatchFilter OR
        cqlSerializers.put(MatchFilterOr.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            infix(b, parenthesizeIfNecessary, insideTokenBrackets, " | ", ((MatchFilterOr)pattern).getClauses());
        });

        // MatchFilter same tokens
        cqlSerializers.put(MatchFilterSameTokens.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            throw new UnsupportedOperationException("Cannot serialize MatchFilterSameTokens to CQL");
        });

        // MatchFilter string
        cqlSerializers.put(MatchFilterString.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            MatchFilterString tp = (MatchFilterString) pattern;
            serializeQuotedString(b, tp.getValue());
        });

        // MatchFilter token annotation
        cqlSerializers.put(MatchFilterTokenAnnotation.class, (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            MatchFilterTokenAnnotation tp = (MatchFilterTokenAnnotation) pattern;
            b.append(tp.getGroupName()).append(".").append(tp.getAnnotationName());
        });

        // MatchFilter token annotation equals string
        cqlSerializers.put(MatchFilterTokenAnnotationEqualsString.class,
                (pattern, b, parenthesizeIfNecessary, insideTokenBrackets) -> {
            throw new UnsupportedOperationException("Cannot serialize MatchFilterTokenAnnotationEqualsString to CQL");
        });
    }

    // Longer serializers below

    interface NodeSerializerBrackets {
        void serialize(boolean parenthesizeIfNecessary, boolean insideTokenBrackets);
    }

    private static void serializeOptBrackets(TextPatternStruct pattern, StringBuilder b,
            boolean parenthesizeIfNecessary, boolean insideTokenBrackets, NodeSerializerBrackets serializer) {
        if (pattern.isBracketQuery() && !insideTokenBrackets) {
            b.append("[");
            serializer.serialize(false, true);
            b.append("]");
        } else {
            serializer.serialize(parenthesizeIfNecessary, insideTokenBrackets);
        }
    }

//    private static void serializeAndNot(TextPatternStruct pattern, StringBuilder b, boolean parenthesizeIfNecessary,
//            boolean insideTokenBrackets) {
//        @SuppressWarnings("deprecation")
//        TextPatternAndNot tp = ((TextPatternAndNot) pattern);
//        if (parenthesizeIfNecessary)
//            b.append("(");
//        infix(b, false, insideTokenBrackets, " & ", tp.include);
//        if (!tp.exclude.isEmpty()) {
//            b.append(" & !");
//            infix(b, tp.exclude.size() > 1, insideTokenBrackets, " & ", tp.exclude);
//        }
//        if (parenthesizeIfNecessary)
//            b.append(")");
//    }

    private static void serializePosFilter(TextPatternStruct pattern, StringBuilder b, boolean parenthesizeIfNecessary,
            boolean insideTokenBrackets) {
        if (insideTokenBrackets)
            throw new UnsupportedOperationException("Cannot serialize TextPatternPositionFilter inside brackets to CQL");
        TextPatternPositionFilter tp = (TextPatternPositionFilter) pattern;
        boolean supportedOp = tp.getOperation() == SpanQueryPositionFilter.Operation.WITHIN ||
                tp.getOperation() == SpanQueryPositionFilter.Operation.CONTAINING;
        if (tp.getAdjustLeading() != 0 || tp.getAdjustTrailing() != 0 || tp.isInvert() | !supportedOp)
            throw new IllegalArgumentException(
                    "Cannot serialize to CorpusQL: posfilter with adjustLeading " + tp.getAdjustLeading() +
                            ", adjustTrailing " + tp.getAdjustTrailing() + ", invert " + tp.isInvert() +
                            ", operation " + tp.getOperation() +
                            " (only supports unadjusted, uninverted within/containing))");
        infix(b, parenthesizeIfNecessary, insideTokenBrackets, " " + tp.getOperation() + " ",
                List.of(tp.getProducer(), tp.getFilter()));
    }

    private static void serializeFuncCall(TextPatternStruct pattern, StringBuilder b, boolean parenthesizeIfNecessary,
            boolean insideTokenBrackets) {
        if (insideTokenBrackets)
            throw new UnsupportedOperationException("Cannot serialize TextPatternQueryFunction inside brackets to CQL");
        TextPatternQueryFunction tp = (TextPatternQueryFunction) pattern;
        b.append(tp.getName()).append("(");
        boolean first = true;
        for (Object arg: tp.getArgs()) {
            if (!first)
                b.append(", ");
            first = false;
            if (arg instanceof TextPattern) {
                serialize((TextPattern) arg, b, false, insideTokenBrackets);
            } else if (arg instanceof String) {
                serializeQuotedString(b, (String) arg);
            } else if (arg instanceof Integer) {
                b.append((int) arg);
            } else {
                b.append(arg);
            }
        }
        b.append(")");
    }

    private static void serializeExpansion(TextPatternStruct pattern, StringBuilder b, boolean parenthesizeIfNecessary,
            boolean insideTokenBrackets) {
        if (insideTokenBrackets)
            throw new UnsupportedOperationException("Cannot serialize TextPatternExpansion inside brackets to CQL");
        TextPatternExpansion tp = (TextPatternExpansion) pattern;
        String any = "[]" + repetitionOperator(tp.getMin(), tp.getMax());
        StringBuilder cl = new StringBuilder();
        serialize(tp.getClause(), cl, true, insideTokenBrackets);
        List<CharSequence> strCl = tp.isExpandToLeft() ? List.of(any, cl) : List.of(cl, any);
        if (parenthesizeIfNecessary)
            b.append("(");
        b.append(StringUtils.join(strCl, " "));
        if (parenthesizeIfNecessary)
            b.append(")");
    }

    private static String repetitionOperator(int min, int max) {
        if (min ==1 && max == 1) {
            return "";
        } else if (min == 0 && max == TextPattern.MAX_UNLIMITED) {
            return "*";
        } else if (min == 1 && max == TextPattern.MAX_UNLIMITED) {
            return "+";
        } else if (min == 0 && max == 1) {
            return "?";
        } else if (max == TextPattern.MAX_UNLIMITED) {
            return "{" + min + ",}";
        } else {
            return "{" + min + "," + max + "}";
        }
    }

    private static StringBuilder serializeQuotedString(StringBuilder b, String regex) {
        return b.append("'").append(regex.replaceAll("[\\\\']", "\\\\$0")).append("'");
    }

    private static String serializeAttributes(Map<String, String> attr) {
        return attr.entrySet().stream()
                .map(e -> e.getKey() + "='" + e.getValue().replaceAll("[\\\\']", "\\\\$0") + "'")
                .collect(Collectors.joining(" "));
    }

    private static void infix(StringBuilder b, boolean parenthesize, boolean insideTokenBrackets, String operator,
            List<? extends TextPatternStruct> clauses) {
        if (parenthesize)
            b.append("(");
        boolean first = true;
        for (TextPatternStruct clause: clauses) {
            if (!first)
                b.append(operator);
            first = false;
            serialize(clause, b, true, insideTokenBrackets);
        }
        if (parenthesize)
            b.append(")");
    }
}

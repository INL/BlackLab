package nl.inl.blacklab.queryParser.corpusql;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.extensions.XFRelations;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.SpanQueryRelations;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternNot;
import nl.inl.blacklab.search.textpattern.TextPatternQueryFunction;
import nl.inl.blacklab.search.textpattern.TextPatternRegex;
import nl.inl.blacklab.search.textpattern.TextPatternRelationMatch;
import nl.inl.blacklab.search.textpattern.TextPatternRelationTarget;
import nl.inl.blacklab.search.textpattern.TextPatternTerm;

public class CorpusQueryLanguageParser {

    private static final boolean USE_TP_RELATION = true;

    /**
     * Parse a Contextual Query Language query.
     * 
     * @param query our query
     * @return the parsed query
     * @throws InvalidQuery on parse error
     */
    public static TextPattern parse(String query, String defaultAnnotation) throws InvalidQuery {
        CorpusQueryLanguageParser parser = new CorpusQueryLanguageParser();
        parser.setDefaultAnnotation(defaultAnnotation);
        return parser.parseQuery(query);
    }

    public static TextPattern parse(String query) throws InvalidQuery {
        return parse(query, AnnotatedFieldNameUtil.DEFAULT_MAIN_ANNOT_NAME);
    }

    /** Automatically add rspan so hit encompasses all matched relations.
     *
     * Only does this if this is a relations query and if setting enabled.
     */
    public TextPattern ensureHitSpansMatchedRelations(TextPattern pattern) {
        boolean addRspanAll = false;
        if (hitsShouldSpanMatchedRelations && pattern.isRelationsQuery()) {
            addRspanAll = true;
            if (pattern instanceof TextPatternQueryFunction) {
                TextPatternQueryFunction qf = (TextPatternQueryFunction) pattern;
                // Only add rspan if not already doing it explicitly
                if (qf.getName().equals(XFRelations.FUNC_RSPAN) || qf.getName().equals(XFRelations.FUNC_REL)) {
                    addRspanAll = false;
                }
            }
        }
        return addRspanAll ? new TextPatternQueryFunction(XFRelations.FUNC_RSPAN,
                List.of(pattern, "all")) : pattern;
    }

    /** Allow strings to be quoted using single quotes? */
    private boolean allowSingleQuotes = true;

    /** If this is a relations query, should we automatically add rspan(..., 'all') so the resulting hit encompasses all
     *  matches relations? */
    private boolean hitsShouldSpanMatchedRelations = true;

    private String defaultAnnotation;

    public CorpusQueryLanguageParser() {
    }

    public TextPattern parseQuery(String query) throws InvalidQuery {
        try {
            GeneratedCorpusQueryLanguageParser parser = new GeneratedCorpusQueryLanguageParser(new StringReader(query));
            parser.wrapper = this;
            return ensureHitSpansMatchedRelations(parser.query());
        } catch (ParseException | TokenMgrError e) {
            throw new InvalidQuery("Error parsing query: " + e.getMessage(), e);
        }
    }

    int num(Token t) {
        return Integer.parseInt(t.toString());
    }

    String chopEnds(String input) {
        if (input.length() >= 2)
            return input.substring(1, input.length() - 1);
        throw new BlackLabRuntimeException("Cannot chop ends off string shorter than 2 chars");
    }

    String getStringBetweenQuotes(String input) throws SingleQuotesException {
        if (!allowSingleQuotes && input.charAt(0) == '\'')
            throw new SingleQuotesException();
        // Eliminate the quotes and unescape backslashes
        return chopEnds(input).replaceAll("\\\\(.)", "$1");
    }

    TextPatternTerm simplePattern(String str) {
        if (str.length() > 0) {
            if (str.charAt(0) != '^')
                str = "^" + str;
            if (str.charAt(str.length() - 1) != '$')
                str += "$";
        }

        // Lucene's regex engine requires double quotes to be escaped, unlike most others.
        // Escape double quotes not already preceded by backslash
        str = str.replaceAll("(?<!\\\\)\"", "\\\\\"");

        // Treat everything like regex now; will be simplified later if possible
        return new TextPatternRegex(str);
    }

    /** Allow strings to be quoted using single quotes? [default: yes] 
     * @param b whether single quotes are allowed */
    public void setAllowSingleQuotes(boolean b) {
        allowSingleQuotes = b;
    }

    /** Allow strings to be quoted using single quotes? */
    boolean isAllowSingleQuotes() {
        return allowSingleQuotes;
    }

    /**
     * Set the default annotation.
     * @param annotation default annotation
     */
    public void setDefaultAnnotation(String annotation) {
        defaultAnnotation = annotation;
    }

    public String getDefaultAnnotation() {
        return defaultAnnotation;
    }

    public void setHitsShouldSpanMatchedRelations(boolean hitsShouldSpanMatchedRelations) {
        this.hitsShouldSpanMatchedRelations = hitsShouldSpanMatchedRelations;
    }

    public boolean isHitsShouldSpanMatchedRelations() {
        return hitsShouldSpanMatchedRelations;
    }

    TextPattern annotationClause(String annot, TextPatternTerm value) {
        // Main annotation has a name. Use that.
        if (annot == null || annot.length() == 0)
            annot = defaultAnnotation;
        return value.withAnnotationAndSensitivity(annot, null);
    }

    /**
     * Get the relation type from the operator.
     *
     * NOTE: if the operator started with ! or ^, this character
     * must have been removed already!
     *
     * @param relationOperator relation operator with optional type regex, e.g. "-det->"
     * @return relation type, e.g. "det", or ".*" if the operator contains no type regex
     */
    static String getTypeRegexFromRelationOperator(String relationOperator) {
        if (!relationOperator.matches("^--?.*--?>$"))
            throw new RuntimeException("Invalid relation operator: " + relationOperator);
        String type = relationOperator.replaceAll("--?>$", "");
        type = type.replaceAll("^--?", "");
        if (type.isEmpty())
            type = ".*"; // any relation
        return type;
    }

    static class ChildRelationStruct {
        public RelationTypeStruct type;
        public TextPattern target;
        public String captureAs;

        public ChildRelationStruct(RelationTypeStruct type, TextPattern target, String captureAs) {
            this.type = type;
            this.target = target;
            this.captureAs = captureAs;
        }
    }

    static class RelationTypeStruct {
        public static RelationTypeStruct fromOperator(String op) {
            boolean negate = false;
            if (op.charAt(0) == '!') {
                negate = true;
                op = op.substring(1);
            }
            String typeRegex = getTypeRegexFromRelationOperator(op);
            return new RelationTypeStruct(typeRegex, negate);
        }

        public String regex;

        public boolean negate;

        public RelationTypeStruct(String regex, boolean negate) {
            this.regex = regex;
            this.negate = negate;
        }
    }

    TextPattern relationQuery(TextPattern parent, List<ChildRelationStruct> childRels) {
        if (USE_TP_RELATION) {
            List<TextPattern> children = new ArrayList<>();
            for (ChildRelationStruct childRel: childRels) {
                TextPattern child = new TextPatternRelationTarget(
                        childRel.type.regex, childRel.type.negate, childRel.target, RelationInfo.SpanMode.SOURCE,
                        SpanQueryRelations.Direction.BOTH_DIRECTIONS, childRel.captureAs);
                children.add(child);
            }
            return new TextPatternRelationMatch(parent, children);
        } else {
            List<TextPattern> clauses = new ArrayList<>();
            clauses.add(parent);
            clauses.addAll(childRels.stream().map(rel -> {
                TextPattern tp = new TextPatternQueryFunction(XFRelations.FUNC_REL,
                        List.of(rel.type.regex, rel.target, "source", rel.captureAs));
                if (rel.type.negate)
                    tp = new TextPatternNot(tp);
                return tp;
            }).collect(Collectors.toList()));
            return new TextPatternQueryFunction(XFRelations.FUNC_RMATCH, clauses);
        }
    }

    TextPattern rootRelationQuery(ChildRelationStruct childRel) {
        assert !childRel.type.negate : "Cannot negate root query";
        if (USE_TP_RELATION) {
            return new TextPatternRelationTarget(
                    childRel.type.regex, false, childRel.target, RelationInfo.SpanMode.TARGET,
                    SpanQueryRelations.Direction.ROOT, childRel.captureAs);
        } else {
            return new TextPatternQueryFunction(
                    XFRelations.FUNC_REL, List.of(childRel.type.regex, childRel.target, "target",
                    childRel.captureAs, "root"));
        }
    }

}

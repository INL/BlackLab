package nl.inl.blacklab.queryParser.corpusql;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.textpattern.RelationOperatorInfo;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternRegex;
import nl.inl.blacklab.search.textpattern.TextPatternRelationMatch;
import nl.inl.blacklab.search.textpattern.RelationTarget;
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

    /** Allow strings to be quoted using single quotes? */
    private boolean allowSingleQuotes = true;

    private String defaultAnnotation;

    public CorpusQueryLanguageParser() {
    }

    public TextPattern parseQuery(String query) throws InvalidQuery {
        try {
            GeneratedCorpusQueryLanguageParser parser = new GeneratedCorpusQueryLanguageParser(new StringReader(query));
            parser.wrapper = this;
            return parser.query();
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

    TextPattern annotationClause(String annot, TextPatternTerm value) {
        // Main annotation has a name. Use that.
        if (annot == null || annot.length() == 0)
            annot = defaultAnnotation;
        return value.withAnnotationAndSensitivity(annot, null);
    }

    static class ChildRelationStruct {

        public final RelationOperatorInfo type;

        public final TextPattern target;

        public final String captureAs;

        public ChildRelationStruct(RelationOperatorInfo type, TextPattern target, String captureAs) {
            this.type = type;
            this.target = target;
            this.captureAs = captureAs;
        }
    }

    TextPattern relationQuery(TextPattern parent, List<ChildRelationStruct> childRels) {
        List<RelationTarget> children = new ArrayList<>();
        String sourceVersion = null;
        for (ChildRelationStruct childRel: childRels) {
            if (childRel.type.getSourceVersion() != null) {
                if (sourceVersion != null && !sourceVersion.equals(childRel.type.getSourceVersion()))
                    throw new RuntimeException("Cannot combine different source versions in one relation query");
                sourceVersion = childRel.type.getSourceVersion();
            }
            RelationTarget child = new RelationTarget(childRel.type, childRel.target,
                    RelationInfo.SpanMode.SOURCE, childRel.captureAs);
            children.add(child);
        }
        return new TextPatternRelationMatch(sourceVersion, parent, children);
    }

    TextPattern rootRelationQuery(ChildRelationStruct childRel) {
        assert !childRel.type.isNegate() : "Cannot negate root query";
        return new TextPatternRelationMatch(null, null,
                List.of(new RelationTarget(childRel.type, childRel.target,
                RelationInfo.SpanMode.TARGET, childRel.captureAs)));
    }

}

package nl.inl.blacklab.queryParser.corpusql;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.SpanQueryRelations;
import nl.inl.blacklab.search.textpattern.TextPattern;
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

    static class RelationOperatorInfo {

        /** Relation or alignment operator, with optional source/target version and relation type regex.
         *  Examples: --> ; -nmod-> ; nl==>de
         */
        private static final Pattern PATT_RELATION_OPERATOR = Pattern.compile("^([a-zA-Z0-9_]*)[-=](.*)[-=]>([a-zA-Z0-9_]*)$");

        /**
         * Get the relation type and target version regexes from the operator.
         * NOTE: if the operator started with ! or ^, this character must have been removed already!
         * If no type was specified, the type will be ".*" (any relation type). If no target version was specified,
         * the target version will be the empty string (any target version or no target version).
         *
         * @param relationOperator     relation operator with optional type regex, e.g. "-det->" or "-det->de"
         * @param negate               is this a negated relation operator? E.g. !-nmod->
         * @param isAlignmentOperator  is this an alignment operator? E.g. ==>
         * @return relation type and target version (intepret both as regexes)
         */
        private static RelationOperatorInfo parseRelationOperator(String relationOperator, boolean negate,
                boolean isAlignmentOperator) {
            Matcher matcher = PATT_RELATION_OPERATOR.matcher(relationOperator);
            if (!matcher.matches())
                throw new RuntimeException("Invalid relation operator: " + relationOperator);
            String sourceVersion = matcher.group(1);
            String typeRegex = matcher.group(2);
            String targetVersion = matcher.group(3);
            if (StringUtils.isEmpty(typeRegex))
                typeRegex = RelationUtil.ANY_TYPE_REGEX; // any relation type
            return new RelationOperatorInfo(typeRegex, sourceVersion, targetVersion, negate, isAlignmentOperator);
        }

        /** Create an info struct from the operator string. */
        public static RelationOperatorInfo fromOperator(String op) {
            boolean negate = false;
            assert op.charAt(0) != '^'; // should've been stripped already
            boolean isAlignmentOperator = op.contains("=>");
            if (op.charAt(0) == '!') {
                negate = true;
                op = op.substring(1);
            }
            return parseRelationOperator(op, negate, isAlignmentOperator);
        }

        /** Relation type regex */
        public final String typeRegex;

        /** Source version we want to search the left side in */
        public final String sourceVersion;

        /** Relation target regex */
        public final String targetVersion;

        /** Is this a negated relation operator? E.g. !-nmod-> */
        public final boolean negate;

        /** Is this an alignment operator? E.g. ==>de ("find all alignment relations between spans on left and right") */
        public final boolean isAlignmentOperator;

        private RelationOperatorInfo(String typeRegex, String sourceVersion, String targetVersion, boolean negate, boolean isAlignmentOperator) {
            this.typeRegex = typeRegex;
            this.sourceVersion = sourceVersion;
            this.targetVersion = targetVersion;
            this.negate = negate;
            this.isAlignmentOperator = isAlignmentOperator;
        }

        /**
         * Get the full relation type regex, optionally including the target version.
         *
         * @return
         */
        public String getFullTypeRegex() {
            // Make sure our type regex has a relation class
            String regex = RelationUtil.optPrependDefaultClass(typeRegex);
            if (targetVersion.isEmpty())
                return regex;
            String[] classAndType = RelationUtil.classAndType(regex);
            String relationClass = classAndType[0];
            String relationType = classAndType[1];
            // A target version was set. Target version must be added to or replaced in type regex.
            // Replace or add target version in relation class
            relationClass = AnnotatedFieldNameUtil.getParallelFieldVersion(relationClass, targetVersion);
            return RelationUtil.fullTypeRegex(relationClass, relationType);
        }
    }

    TextPattern relationQuery(TextPattern parent, List<ChildRelationStruct> childRels) {
        List<TextPattern> children = new ArrayList<>();
        String sourceVersion = null;
        for (ChildRelationStruct childRel: childRels) {
            if (childRel.type.sourceVersion != null) {
                if (sourceVersion != null && !sourceVersion.equals(childRel.type.sourceVersion))
                    throw new RuntimeException("Cannot combine different source versions in one relation query");
                sourceVersion = childRel.type.sourceVersion;
            }
            TextPattern child = new TextPatternRelationTarget(
                    childRel.type.getFullTypeRegex(), childRel.type.negate, childRel.target, RelationInfo.SpanMode.SOURCE,
                    SpanQueryRelations.Direction.BOTH_DIRECTIONS, childRel.captureAs, childRel.type.sourceVersion,
                    childRel.type.targetVersion);
            children.add(child);
        }
        return new TextPatternRelationMatch(sourceVersion, parent, children);
    }

    TextPattern rootRelationQuery(ChildRelationStruct childRel) {
        assert !childRel.type.negate : "Cannot negate root query";
        return new TextPatternRelationTarget(
                childRel.type.getFullTypeRegex(), false, childRel.target, RelationInfo.SpanMode.TARGET,
                SpanQueryRelations.Direction.ROOT, childRel.captureAs, childRel.type.sourceVersion,
                childRel.type.targetVersion);
    }

}

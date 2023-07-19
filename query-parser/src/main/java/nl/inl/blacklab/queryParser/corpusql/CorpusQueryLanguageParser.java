package nl.inl.blacklab.queryParser.corpusql;

import java.io.StringReader;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternAnnotation;
import nl.inl.blacklab.search.textpattern.TextPatternRegex;

public class CorpusQueryLanguageParser {
    
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
        return chopEnds(input);
    }

    TextPattern simplePattern(String str) {
        if (str.length() > 0) {
            if (str.charAt(0) != '^')
                str = "^" + str;
            if (str.charAt(str.length() - 1) != '$')
                str += "$";
        }

        // Treat everything like regex now; will be simplified later if possible
        return new TextPatternRegex(str);
    }

    /** Allow strings to be quoted using single quotes? [default: yes] 
     * @param b whether single quotes are allowed */
    public void setAllowSingleQuotes(boolean b) {
        allowSingleQuotes = b;
    }

    /** Allow strings to be quoted using single quotes? */
    boolean getAllowSingleQuotes() {
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

    TextPattern annotationClause(String annot, TextPattern value) {
        // Main annotation has a name. Use that.
        if (annot == null || annot.length() == 0)
            annot = defaultAnnotation;
        return new TextPatternAnnotation(annot, value);
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
    String getTypeRegexFromRelationOperator(String relationOperator) {
        if (!relationOperator.matches("^--?.*--?>$"))
            throw new RuntimeException("Invalid relation operator: " + relationOperator);
        String type = relationOperator.replaceAll("--?>$", "");
        type = type.replaceAll("^--?", "");
        if (type.isEmpty())
            type = ".*"; // any relation
        return type;
    }

}

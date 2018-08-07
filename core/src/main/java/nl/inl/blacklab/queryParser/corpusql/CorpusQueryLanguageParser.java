package nl.inl.blacklab.queryParser.corpusql;

import java.io.StringReader;

import nl.inl.blacklab.search.BlackLabException;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternAnnotation;
import nl.inl.blacklab.search.textpattern.TextPatternRegex;

public class CorpusQueryLanguageParser {
    
    /**
     * Parse a Contextual Query Language query.
     * 
     * @param query our query
     * @return the parsed query
     * @throws ParseException on parse error
     */
    public static TextPattern parse(String query) throws ParseException {
        CorpusQueryLanguageParser parser = new CorpusQueryLanguageParser();
        return parser.parseQuery(query);
    }

    /** Allow strings to be quoted using single quotes? */
    private boolean allowSingleQuotes = true;

    private String defaultAnnotation = "word";

    public CorpusQueryLanguageParser() {
    }

    public TextPattern parseQuery(String query) throws ParseException {
        GeneratedCorpusQueryLanguageParser parser = new GeneratedCorpusQueryLanguageParser(new StringReader(query));
        parser.wrapper = this;
        return parser.query();
    }

    int num(Token t) {
        return Integer.parseInt(t.toString());
    }

    String chopEnds(String input) {
        if (input.length() >= 2)
            return input.substring(1, input.length() - 1);
        throw new BlackLabException();
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

    public void setDefaultAnnotation(IndexMetadata indexMetadata, String fieldName) {
        defaultAnnotation = indexMetadata.annotatedFields().get(fieldName).annotations().main().name();
    }

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

}

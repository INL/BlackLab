package nl.inl.blacklab.queryParser.corpusql;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.textpattern.TextPattern;

public class CQL extends CorpusQueryLanguageParser {
    
    /**
     * Parse a Contextual Query Language query.
     * 
     * @param query our query
     * @return the parsed query
     * @throws InvalidQuery on parse error
     */
    public static TextPattern toTextPattern(String query) throws InvalidQuery {
        CQL parser = new CQL();
        return parser.parseQuery(query);
    }

}

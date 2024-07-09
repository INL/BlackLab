package nl.inl.blacklab.querytool;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.textpattern.TextPattern;

/**
 * Generic command parser interface
 */
abstract class Parser {
    public abstract String getPrompt();

    public abstract String getName();

    public abstract TextPattern parse(BlackLabIndex index, String query) throws InvalidQuery;

    /**
     * Get the filter query included in the last query, if any. Only used for
     * ContextQL.
     *
     * @return the filter query, or null if there was none
     */
    Query getIncludedFilterQuery() {
        return null;
    }

    public abstract void printHelp(Output output);
}

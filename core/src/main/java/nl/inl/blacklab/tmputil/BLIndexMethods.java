package nl.inl.blacklab.tmputil;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.exceptions.WildcardTermTooBroad;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.QueryExplanation;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryFiltered;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.textpattern.TextPattern;

public class BLIndexMethods {
    /**
     * Explain how a TextPattern is converted to a SpanQuery and rewritten to an
     * optimized version to be executed by Lucene.
     * 
     * @param index the index
     * @param queryInfo query info, such as the field to search
     * @param pattern the pattern to explain
     * @param filter filter query, or null for none
     * @return the explanation
     * @throws WildcardTermTooBroad if a wildcard or regular expression term is
     *             overly broad
     * @throws InvalidQuery
     */
    public static QueryExplanation explain(BlackLabIndex index, QueryInfo queryInfo, TextPattern pattern, Query filter)
            throws InvalidQuery {
        return index.explain(createSpanQuery(index, queryInfo.withIndex(index), pattern, filter));
    }

    public static BLSpanQuery createSpanQuery(BlackLabIndex index, QueryInfo queryInfo, TextPattern pattern,
            Query filter) throws InvalidQuery {
        // Convert to SpanQuery
        //pattern = pattern.rewrite();
        BLSpanQuery spanQuery = pattern.translate(index.defaultExecutionContext(queryInfo.field()));
        if (filter != null)
            spanQuery = new SpanQueryFiltered(spanQuery, filter);
        return spanQuery;
    }

    /**
     * Find hits for a pattern in a field.
     * 
     * @param index the index
     * @param queryInfo information about the query: field, logger
     * @param pattern the pattern to find
     * @param filter determines which documents to search
     * @param settings search settings, or null for default
     * @return the hits found
     * @throws WildcardTermTooBroad if a wildcard or regular expression term is
     *             overly broad
     * @throws InvalidQuery
     */
    public static Hits find(BlackLabIndex index, QueryInfo queryInfo, TextPattern pattern, Query filter, SearchSettings settings)
            throws InvalidQuery {
        BLSpanQuery spanQuery = pattern.translate(index.defaultExecutionContext(queryInfo.field()));
        if (filter != null)
            spanQuery = new SpanQueryFiltered(spanQuery, filter);
        return index.find(spanQuery, settings, queryInfo.searchLogger());
    }

}

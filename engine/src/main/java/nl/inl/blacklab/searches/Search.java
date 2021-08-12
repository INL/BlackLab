package nl.inl.blacklab.searches;

import java.util.concurrent.Future;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.requestlogging.LogLevel;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchResult;

/**
 * A 'recipe' of search operations.
 *
 * @param <R> results type, e.g. Hits
 */
public interface Search<R extends SearchResult> {

    /**
     * Execute the search operation, returning the final response.
     *
     * Executes the search synchronously.
     *
     * @return result of the operation
     * @throws InvalidQuery
     */
    R execute() throws InvalidQuery;

    /**
     * Execute the search operation asynchronously.
     *
     * Runs the search in a separate thread and passes the result through
     * the returned Future.
     *
     * @return future result
     */
    Future<R> executeAsync();

    R executeInternal() throws InvalidQuery;

    @Override
    boolean equals(Object obj);

    @Override
    int hashCode();

    QueryInfo queryInfo();

    @Override
    String toString();

    /**
     * Log details about the search's execution.
     * @param level log level
     * @param msg message to log
     */
    default void log(LogLevel level, String msg) {
        queryInfo().log(level, msg);
    }

}
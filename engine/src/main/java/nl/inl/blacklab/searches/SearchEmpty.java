package nl.inl.blacklab.searches;

import java.util.Arrays;
import java.util.Objects;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchResult;
import nl.inl.blacklab.search.results.SearchSettings;

/** 
 * Empty search that just knows about its index and annotated field to search,
 * and serves as a starting point for actual searches.
 */
public class SearchEmpty extends AbstractSearch<SearchResult> {
    
    public SearchEmpty(QueryInfo queryInfo) {
        super(queryInfo);
    }

    @Override
    public SearchResult executeInternal(ActiveSearch<SearchResult> activeSearch) {
        throw new UnsupportedOperationException();
    }

    public SearchHits find(BLSpanQuery query, SearchSettings searchSettings) {
        if (searchSettings == null) {
            // If no settings given, use the default
            searchSettings = queryInfo().index().searchSettings();
        }
        return new SearchHitsFromBLSpanQuery(queryInfo(), query, searchSettings);
    }

    public SearchHits find(BLSpanQuery query) {
        return find(query, null);
    }

    public SearchHits find(int[] docs, int[] starts, int[] ends, SearchSettings searchSettings) {
        if (searchSettings == null) {
            // If no settings given, use the default
            searchSettings = queryInfo().index().searchSettings();
        }
        return reconstructImpl(docs, starts, ends, searchSettings);
    }

    private SearchHits reconstructImpl(int[] docs, int[] starts, int[] ends, SearchSettings searchSettings) {
        return new SearchHits(queryInfo()) {
            @Override
            public SearchSettings searchSettings() { return searchSettings; }

            @Override
            public Hits executeInternal(ActiveSearch<Hits> activeSearch) throws InvalidQuery {
                BlackLabIndex index = queryInfo().index();
                Hits hits = Hits.list(QueryInfo.create(index, index.mainAnnotatedField(), queryInfo().useCache()), docs, starts, ends);
                return hits;
            }

            @Override
            public int hashCode() {
                return Objects.hash(super.hashCode(), Arrays.hashCode(docs), Arrays.hashCode(starts), Arrays.hashCode(ends));
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj)
                    return true;
                if (!super.equals(obj))
                    return false;
                if (getClass() != obj.getClass())
                    return false;
                return this.hashCode() == obj.hashCode();
            }
        };
    }
    
    public SearchDocs findDocuments(Query documentQuery) {
        return new SearchDocsFromQuery(queryInfo(), documentQuery);
    }
    
    @Override
    public String toString() {
        return toString("empty");
    }
    
}

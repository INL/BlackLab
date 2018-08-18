package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.QueryInfo;

/** A search that yields groups of hits. */
public class SearchDocGroupsFromDocs extends SearchDocGroups {
    
    private SearchDocs source;

    private DocProperty property;

    private int maxDocs;

    public SearchDocGroupsFromDocs(QueryInfo queryInfo, List<SearchResultObserver> ops, SearchDocs source, DocProperty property, int maxDocsToStorePerGroup) {
        super(queryInfo, ops);
        this.source = source;
        this.property = property;
        this.maxDocs = maxDocsToStorePerGroup;
    }
    
    @Override
    public DocGroups execute() throws InvalidQuery {
        return notifyObservers(source.execute().groupedBy(property, maxDocs));
    }

    @Override
    public SearchDocGroupsFromDocs observe(SearchResultObserver operation) {
        return new SearchDocGroupsFromDocs(queryInfo(), extraObserver(operation), source, property, maxDocs);
    }
    
}

package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.QueryInfo;

/** A search that yields groups of hits. */
public class SearchDocGroupsFromDocs extends SearchDocGroups {
    
    private SearchDocs source;

    private DocProperty property;

    private int maxDocs;

    public SearchDocGroupsFromDocs(QueryInfo queryInfo, SearchDocs source, DocProperty property, int maxDocsToStorePerGroup) {
        super(queryInfo);
        this.source = source;
        this.property = property;
        this.maxDocs = maxDocsToStorePerGroup;
    }
    
    @Override
    public DocGroups execute() throws InvalidQuery {
        return source.execute().groupedBy(property, maxDocs);
    }
    
}

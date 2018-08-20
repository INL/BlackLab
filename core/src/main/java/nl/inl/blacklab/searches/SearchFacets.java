package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.search.results.Facets;
import nl.inl.blacklab.search.results.QueryInfo;

/** A search that yields multiple doc groupings with only counts (no stored results). */
public class SearchFacets extends AbstractSearch<Facets> {
    
    private SearchDocs source;
    private List<DocProperty> properties;

    public SearchFacets(QueryInfo queryInfo, SearchDocs source, List<DocProperty> properties) {
        super(queryInfo);
        this.source = source;
        this.properties = properties;
    }

    @Override
    protected Facets executeInternal() throws InvalidQuery {
        return new Facets(source.execute(), properties);
    }
    
}

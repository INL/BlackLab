package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SampleParameters;

/** A search that produces DocResults. */
public abstract class SearchDocs extends AbstractSearch {

    public SearchDocs(QueryInfo queryInfo, List<SearchResultObserver> customOperations) {
        super(queryInfo, customOperations);
    }

    @Override
    public abstract DocResults execute() throws InvalidQuery;

    /**
     * Group hits by a property.
     * 
     * @param groupBy what to group by
     * @param maxResultsToGatherPerGroup how many results to gather per group
     * @return resulting operation
     */
    public SearchDocGroups group(DocProperty groupBy, int maxResultsToGatherPerGroup) {
        return new SearchDocGroupsFromDocs(queryInfo(), (List<SearchResultObserver>)null, this, groupBy, maxResultsToGatherPerGroup);
    }

    /**
     * Sort hits.
     * 
     * @param sortBy what to sort by
     * @return resulting operation
     */
    public SearchDocs sort(DocProperty sortBy) {
        return new SearchDocsSorted(queryInfo(), (List<SearchResultObserver>)null, this, sortBy);
    }
    
    /**
     * Sample hits.
     * 
     * @param par how many hits to sample; seed
     * @return resulting operation
     */
    public SearchDocs sample(SampleParameters par) {
        return new SearchDocsSampled(queryInfo(), (List<SearchResultObserver>)null, this, par);
    }

    /**
     * Get hits with a certain property value.
     * 
     * @param property property to test 
     * @param value value to test for
     * @return resulting operation
     */
    public SearchDocs filter(DocProperty property, PropertyValue value) {
        return new SearchDocsFiltered(queryInfo(), (List<SearchResultObserver>)null, this, property, value);
    }

    /**
     * Get window of hits.
     * 
     * @param first first hit to select
     * @param number number of hits to select
     * @return resulting operation
     */
    public SearchDocs window(int first, int number) {
        return new SearchDocsWindow(queryInfo(), (List<SearchResultObserver>)null, this, first, number);
    }
    
}

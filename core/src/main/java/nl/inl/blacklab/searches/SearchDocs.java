package nl.inl.blacklab.searches;

import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SampleParameters;

/** A search that produces DocResults. */
public abstract class SearchDocs extends AbstractSearch<DocResults> {

    public SearchDocs(QueryInfo queryInfo) {
        super(queryInfo);
    }
    
    /**
     * Group hits by a property.
     * 
     * @param groupBy what to group by
     * @param maxResultsToGatherPerGroup how many results to gather per group
     * @return resulting operation
     */
    public SearchDocGroups group(DocProperty groupBy, int maxResultsToGatherPerGroup) {
        return new SearchDocGroupsFromDocs(queryInfo(), this, groupBy, maxResultsToGatherPerGroup);
    }

    /**
     * Sort hits.
     * 
     * @param sortBy what to sort by
     * @return resulting operation
     */
    public SearchDocs sort(DocProperty sortBy) {
        return new SearchDocsSorted(queryInfo(), this, sortBy);
    }
    
    /**
     * Sample hits.
     * 
     * @param par how many hits to sample; seed
     * @return resulting operation
     */
    public SearchDocs sample(SampleParameters par) {
        return new SearchDocsSampled(queryInfo(), this, par);
    }

    /**
     * Get hits with a certain property value.
     * 
     * @param property property to test 
     * @param value value to test for
     * @return resulting operation
     */
    public SearchDocs filter(DocProperty property, PropertyValue value) {
        return new SearchDocsFiltered(queryInfo(), this, property, value);
    }

    /**
     * Get window of hits.
     * 
     * @param first first hit to select
     * @param number number of hits to select
     * @return resulting operation
     */
    public SearchDocs window(int first, int number) {
        return new SearchDocsWindow(queryInfo(), this, first, number);
    }
    
    @Override
    public abstract boolean equals(Object obj);
    
    @Override
    public abstract int hashCode();
    
}

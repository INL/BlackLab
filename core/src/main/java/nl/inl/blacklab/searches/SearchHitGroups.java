package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.HitGroupProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.results.HitGroup;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SampleParameters;

/** A search that yields groups of hits. */
public abstract class SearchHitGroups extends SearchResults<HitGroup> {
    
    public SearchHitGroups(QueryInfo queryInfo, List<SearchResultObserver> ops) {
        super(queryInfo, ops);
    }
    
    @Override
    public abstract HitGroups execute() throws InvalidQuery;

    /**
     * Sort hits.
     * 
     * @param sortBy what to sort by
     * @return resulting operation
     */
    public SearchHitGroups sort(ResultProperty<HitGroup> sortBy) {
        return new SearchHitGroupsSorted(queryInfo(), null, this, sortBy);
    }
    
    /**
     * Sample hits.
     * 
     * @param par how many hits to sample; seed
     * @return resulting operation
     */
    public SearchHitGroups sample(SampleParameters par) {
        return new SearchHitGroupsSampled(queryInfo(), (List<SearchResultObserver>)null, this, par);
    }

    /**
     * Get hits with a certain property value.
     * 
     * @param property property to test 
     * @param value value to test for
     * @return resulting operation
     */
    public SearchHitGroups filter(HitGroupProperty property, PropertyValue value) {
        return new SearchHitGroupsFiltered(queryInfo(), (List<SearchResultObserver>)null, this, property, value);
    }

    /**
     * Get window of hits.
     * 
     * @param first first hit to select
     * @param number number of hits to select
     * @return resulting operation
     */
    public SearchHitGroups window(int first, int number) {
        return new SearchHitGroupsWindow(queryInfo(), (List<SearchResultObserver>)null, this, first, number);
    }

}

package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.results.HitGroup;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.QueryInfo;

/** A search that yields groups of hits. */
public abstract class SearchHitGroups extends AbstractSearch {
    
    public SearchHitGroups(QueryInfo queryInfo, List<SearchOperation> ops) {
        super(queryInfo, ops);
    }

    /**
     * Sort hits.
     * 
     * @param sortBy what to sort by
     * @return resulting operation
     */
    public SearchHitGroups sortBy(ResultProperty<HitGroup> sortBy) {
        return new SearchHitGroupsSorted(queryInfo(), null, this, sortBy);
    }
    
    @Override
    public abstract HitGroups execute() throws InvalidQuery;
}

package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.RegexpTooLarge;
import nl.inl.blacklab.exceptions.WildcardTermTooBroad;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;

/** A search that yields hits. */
public class SearchHitsFiltered extends SearchHits {

    private SearchHits source;
    private HitProperty property;
    private PropertyValue value;

    SearchHitsFiltered(QueryInfo queryInfo, SearchHits source, HitProperty property, PropertyValue value) {
        super(queryInfo);
        this.source = source;
        this.property = property;
        this.value = value;
    }
    
    @Override
    public Hits execute() throws WildcardTermTooBroad, RegexpTooLarge {
        return source.execute().filteredBy(property, value);
    }
}

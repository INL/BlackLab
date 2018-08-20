package nl.inl.blacklab.search.results;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.resultproperty.DocProperty;

public class Facets implements SearchResult {
    
    private List<DocProperty> facets;
    
    private Map<DocProperty, DocGroups> counts;

    public Facets(DocResults source, List<DocProperty> facets) {
        this.facets = facets;
        counts = new HashMap<>();
        for (DocProperty facetBy : facets) {
            counts.put(facetBy, source.group(facetBy, 0));
        }
    }

    public List<DocProperty> facets() {
        return facets;
    }

    public Map<DocProperty, DocGroups> countsPerFacet() {
        return counts;
    }

}

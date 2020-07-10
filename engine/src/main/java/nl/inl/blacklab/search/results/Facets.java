package nl.inl.blacklab.search.results;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.resultproperty.DocProperty;

public class Facets implements SearchResult {
    
    private List<DocProperty> facets;
    
    private Map<DocProperty, DocGroups> counts;
    
    private int resultObjects = 0;

    public Facets(DocResults source, List<DocProperty> facets) {
        this.facets = facets;
        counts = new HashMap<>();
        for (DocProperty facetBy : facets) {
            DocGroups groups = source.group(facetBy, 0);
            counts.put(facetBy, groups);
            resultObjects += groups.size();
        }
    }

    public List<DocProperty> facets() {
        return facets;
    }

    public Map<DocProperty, DocGroups> countsPerFacet() {
        return counts;
    }

    @Override
    public int numberOfResultObjects() {
        return resultObjects;
    }

}

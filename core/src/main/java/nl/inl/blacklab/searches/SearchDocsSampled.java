package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SampleParameters;

public class SearchDocsSampled extends SearchDocs {

    private SearchDocs docsSearch;

    private SampleParameters sampleParameters;

    public SearchDocsSampled(QueryInfo queryInfo, SearchDocs docsSearch, SampleParameters sampleParameters) {
        super(queryInfo);
        this.docsSearch = docsSearch;
        this.sampleParameters = sampleParameters;
    }

    @Override
    public DocResults execute() throws InvalidQuery {
        return docsSearch.execute().sample(sampleParameters);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((docsSearch == null) ? 0 : docsSearch.hashCode());
        result = prime * result + ((sampleParameters == null) ? 0 : sampleParameters.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SearchDocsSampled other = (SearchDocsSampled) obj;
        if (docsSearch == null) {
            if (other.docsSearch != null)
                return false;
        } else if (!docsSearch.equals(other.docsSearch))
            return false;
        if (sampleParameters == null) {
            if (other.sampleParameters != null)
                return false;
        } else if (!sampleParameters.equals(other.sampleParameters))
            return false;
        return true;
    }

}

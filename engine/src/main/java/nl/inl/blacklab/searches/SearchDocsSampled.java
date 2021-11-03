package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SampleParameters;

public class SearchDocsSampled extends SearchDocs {

    private SearchDocs source;

    private SampleParameters sampleParameters;

    public SearchDocsSampled(QueryInfo queryInfo, SearchDocs docsSearch, SampleParameters sampleParameters) {
        super(queryInfo);
        this.source = docsSearch;
        this.sampleParameters = sampleParameters;
    }

    @Override
    public DocResults executeInternal() throws InvalidQuery {
        return source.executeNoQueue().sample(sampleParameters);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((sampleParameters == null) ? 0 : sampleParameters.hashCode());
        result = prime * result + ((source == null) ? 0 : source.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        SearchDocsSampled other = (SearchDocsSampled) obj;
        if (sampleParameters == null) {
            if (other.sampleParameters != null)
                return false;
        } else if (!sampleParameters.equals(other.sampleParameters))
            return false;
        if (source == null) {
            if (other.source != null)
                return false;
        } else if (!source.equals(other.source))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return toString("sample", source, sampleParameters);
    }

}

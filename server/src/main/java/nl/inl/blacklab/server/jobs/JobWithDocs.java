package nl.inl.blacklab.server.jobs;

import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.search.SearchManager;

/**
 * A search job that produces a Hits object
 */
public abstract class JobWithDocs extends Job {

    DocResults docResults;

    public JobWithDocs(SearchManager searchMan, User user, JobDescription par) throws BlsException {
        super(searchMan, user, par);
    }

    public DocResults getDocResults() {
        return docResults;
    }

    @Override
    protected void dataStreamSubclassEntries(DataStream ds) {
        //
    }

    @Override
    protected void cleanup() {
        docResults = null;
        super.cleanup();
    }

    @Override
    protected DocResults getObjectToPrioritize() {
        return docResults;
    }
}

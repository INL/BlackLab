package nl.inl.blacklab.server.jobs;

import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.HitsSample;
import nl.inl.blacklab.search.results.SampleParameters;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.requesthandlers.SearchParameters;
import nl.inl.blacklab.server.search.SearchManager;

/**
 * Sample hits from a Hits object
 */
public class JobHitsSample extends JobWithHits {

    public static class JobDescSampleHits extends JobDescription {

        SampleParameters sampleSettings;

        public JobDescSampleHits(SearchParameters param, JobDescription hitsToSample, SearchSettings searchSettings,
                SampleParameters settings) {
            super(param, JobHitsSample.class, hitsToSample, searchSettings);
            this.sampleSettings = settings;
        }

        @Override
        public SampleParameters getSampleSettings() {
            return sampleSettings;
        }

        @Override
        public String uniqueIdentifier() {
            return super.uniqueIdentifier() + sampleSettings + ")";
        }

        @Override
        public void dataStreamEntries(DataStream ds) {
            super.dataStreamEntries(ds);
            ds.entry("sampleSettings", sampleSettings);
        }

        @Override
        public String getUrlPath() {
            return "hits";
        }

    }

    public JobHitsSample(SearchManager searchMan, User user, JobDescription par) throws BlsException {
        super(searchMan, user, par);
    }

    @Override
    protected void performSearch() throws BlsException {
        Hits inputHits = ((JobWithHits) inputJob).getHits();
        hits = HitsSample.fromHits(inputHits, jobDesc.getSampleSettings());
    }

}

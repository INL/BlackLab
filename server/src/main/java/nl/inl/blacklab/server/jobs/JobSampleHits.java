package nl.inl.blacklab.server.jobs;


import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.HitsSample;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.search.SearchManager;

/**
 * Sample hits from a Hits object
 */
public class JobSampleHits extends JobWithHits {

	public static class JobDescSampleHits extends JobDescription {

		SampleSettings sampleSettings;

		public JobDescSampleHits(JobDescription hitsToSample, SampleSettings settings) {
			super(JobSampleHits.class, hitsToSample);
			this.sampleSettings = settings;
		}

		@Override
		public SampleSettings getSampleSettings() {
			return sampleSettings;
		}

		@Override
		public String uniqueIdentifier() {
			return super.uniqueIdentifier() + sampleSettings + ")";
		}

		@Override
		public void dataStreamEntries(DataStream ds) {
			super.dataStreamEntries(ds);
			ds	.entry("sampleSettings", sampleSettings);
		}

	}

	public JobSampleHits(SearchManager searchMan, User user, JobDescription par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		Hits inputHits = ((JobWithHits)inputJob).getHits();
		SampleSettings sample = jobDesc.getSampleSettings();
		if (sample.percentage() >= 0) {
			hits = HitsSample.fromHits(inputHits, sample.percentage() / 100f, sample.seed());
		} else if (sample.number() >= 0) {
			hits = HitsSample.fromHits(inputHits, sample.number(), sample.seed());
		}
	}

}

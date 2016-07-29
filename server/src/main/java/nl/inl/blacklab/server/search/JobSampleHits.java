package nl.inl.blacklab.server.search;


import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.HitsSample;
import nl.inl.blacklab.server.dataobject.DataObject;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BlsException;

/**
 * Sample hits from a Hits object
 */
public class JobSampleHits extends JobWithHits {

	public static class JobDescSampleHits extends JobDescriptionBasic {

		JobDescription inputDesc;

		SampleSettings sampleSettings;

		public JobDescSampleHits(String indexName, JobDescription hitsToSample, SampleSettings settings) {
			super(indexName);
			this.inputDesc = hitsToSample;
			this.sampleSettings = settings;
		}

		public JobDescription getInputDesc() {
			return inputDesc;
		}

		@Override
		public SampleSettings getSampleSettings() {
			return sampleSettings;
		}

		@Override
		public String uniqueIdentifier() {
			return "JDSampleHits [" + indexName + ", " + inputDesc + ", " + sampleSettings + "]";
		}

		@Override
		public Job createJob(SearchManager searchMan, User user) throws BlsException {
			return new JobSampleHits(searchMan, user, this);
		}

		@Override
		public DataObject toDataObject() {
			DataObjectMapElement o = new DataObjectMapElement();
			o.put("jobClass", "JobSampleHits");
			o.put("indexName", indexName);
			o.put("inputDesc", inputDesc.toDataObject());
			o.put("sampleSettings", sampleSettings.toString());
			return o;
		}

	}

	public JobSampleHits(SearchManager searchMan, User user, JobDescSampleHits par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		JobDescSampleHits sampleDesc = (JobDescSampleHits)jobDesc;
		JobDescription inputDesc = sampleDesc.getInputDesc();
		JobWithHits inputJob;
		inputJob = (JobWithHits) searchMan.search(user, inputDesc);
		waitForJobToFinish(inputJob);
		Hits inputHits = inputJob.getHits();
		SampleSettings sample = sampleDesc.getSampleSettings();
		if (sample.percentage() >= 0) {
			hits = HitsSample.fromHits(inputHits, sample.percentage() / 100f, sample.seed());
		} else if (sample.number() >= 0) {
			hits = HitsSample.fromHits(inputHits, sample.number(), sample.seed());
		}
	}

}

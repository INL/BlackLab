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

	public static class Description extends Job.BasicDescription {

		Description inputJob;

		SampleSettings sampleSettings;

		public Description(String indexName, Description hitsToSample, SampleSettings settings) {
			super(indexName);
			this.inputJob = hitsToSample;
			this.sampleSettings = settings;
		}

		public Description getInputDesc() {
			return inputJob;
		}

		@Override
		public SampleSettings getSampleSettings() {
			return sampleSettings;
		}

		@Override
		public String uniqueIdentifier() {
			return "SampleHits [" + inputJob + ", " + sampleSettings + "]";
		}

		@Override
		public Job createJob(SearchManager searchMan, User user) throws BlsException {
			return new JobSampleHits(searchMan, user, this);
		}

		@Override
		public DataObject toDataObject() {
			DataObjectMapElement o = new DataObjectMapElement();
			o.put("jobClass", "JobSampleHits");
			o.put("inputJob", inputJob.uniqueIdentifier());
			o.put("sampleSettings", sampleSettings.toString());
			return o;
		}

	}

	public JobSampleHits(SearchManager searchMan, User user, Description par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		Description sampleDesc = (JobSampleHits.Description)jobDesc;
		Description inputDesc = sampleDesc.getInputDesc();
		JobWithHits inputJob;
		if (inputDesc.hasSort())
			inputJob = (JobWithHits) searchMan.search(user, inputDesc.hitsSorted());
		else
			inputJob = (JobWithHits) searchMan.search(user, inputDesc.hits());
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

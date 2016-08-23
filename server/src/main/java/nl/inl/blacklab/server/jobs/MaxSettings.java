package nl.inl.blacklab.server.jobs;

public class MaxSettings {

	private int maxRetrieve;

	private int maxCount;

	public MaxSettings(int maxRetrieve, int maxCount) {
		this.maxRetrieve = maxRetrieve;
		this.maxCount = maxCount;
	}

	public int maxRetrieve() {
		return maxRetrieve;
	}

	public int maxCount() {
		return maxCount;
	}

	@Override
	public String toString() {
		return "maxRetrieve=" + maxRetrieve + ", maxCount=" + maxCount;
	}

}

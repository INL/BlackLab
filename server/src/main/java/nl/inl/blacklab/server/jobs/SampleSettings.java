package nl.inl.blacklab.server.jobs;

public class SampleSettings {

	private float percentage;

	private int number;

	private long seed;

	public SampleSettings(float percentage, int number, long seed) {
		this.percentage = percentage;
		this.number = number;
		this.seed = seed;
	}

	public float percentage() {
		return percentage;
	}

	public int number() {
		return number;
	}

	public long seed() {
		return seed;
	}

	@Override
	public String toString() {
		return "sampleperc=" + percentage + ", samplenum=" + number + ", sampleseed=" + seed;
	}

}

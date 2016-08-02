package nl.inl.blacklab.server.jobs;

public class HitGroupSettings {
	private String groupBy;

	public HitGroupSettings(String groupBy) {
		super();
		this.groupBy = groupBy;
	}

	public String groupBy() {
		return groupBy;
	}

	@Override
	public String toString() {
		return "hitgroup=" + groupBy;
	}

}

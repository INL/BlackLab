package nl.inl.blacklab.server.search;

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
		return "HitGroupSettings [groupBy=" + groupBy + "]";
	}

}

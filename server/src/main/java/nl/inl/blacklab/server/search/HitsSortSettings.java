package nl.inl.blacklab.server.search;

public class HitsSortSettings {

	private String sortBy;

	private boolean reverse;

	public HitsSortSettings(String sortBy, boolean reverse) {
		super();
		this.sortBy = sortBy;
		this.reverse = reverse;
	}

	public String sortBy() {
		return sortBy;
	}

	public boolean reverse() {
		return reverse;
	}

	@Override
	public String toString() {
		return "HitsSortSettings [sortBy=" + sortBy + ", reverse=" + reverse + "]";
	}

}

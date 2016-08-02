package nl.inl.blacklab.server.jobs;

public class HitSortSettings {

	private String sortBy;

	private boolean reverse;

	public HitSortSettings(String sortBy, boolean reverse) {
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
		return "hitsort=" + sortBy + ", sortreverse=" + reverse;
	}

}

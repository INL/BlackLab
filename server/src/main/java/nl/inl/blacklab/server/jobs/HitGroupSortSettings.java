package nl.inl.blacklab.server.jobs;

import nl.inl.blacklab.search.grouping.GroupProperty;

public class HitGroupSortSettings {

	private GroupProperty sortBy;

	private boolean reverse;

	public HitGroupSortSettings(GroupProperty sortBy, boolean reverse) {
		super();
		this.sortBy = sortBy;
		this.reverse = reverse;
	}

	public GroupProperty sortBy() {
		return sortBy;
	}

	public boolean reverse() {
		return reverse;
	}

	@Override
	public String toString() {
		return "hitgroupsort=" + sortBy + ", sortreverse=" + reverse;
	}

}

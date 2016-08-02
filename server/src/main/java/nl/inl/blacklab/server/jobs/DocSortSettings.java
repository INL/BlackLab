package nl.inl.blacklab.server.jobs;

import nl.inl.blacklab.perdocument.DocProperty;

public class DocSortSettings {
	private DocProperty sortBy;

	private boolean reverse;

	public DocSortSettings(DocProperty sortBy, boolean reverse) {
		super();
		this.sortBy = sortBy;
		this.reverse = reverse;
	}

	public DocProperty sortBy() {
		return sortBy;
	}

	public boolean reverse() {
		return reverse;
	}

	@Override
	public String toString() {
		return "docsort=" + sortBy.serialize() + ", sortreverse=" + reverse;
	}

}

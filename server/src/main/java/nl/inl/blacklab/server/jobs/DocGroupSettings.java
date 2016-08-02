package nl.inl.blacklab.server.jobs;

import nl.inl.blacklab.perdocument.DocProperty;

public class DocGroupSettings {

	DocProperty groupBy;

	public DocGroupSettings(DocProperty groupBy) {
		super();
		this.groupBy = groupBy;
	}

	public DocProperty groupBy() {
		return groupBy;
	}

	@Override
	public String toString() {
		return "docgroup=" + groupBy.serialize();
	}

}

package nl.inl.blacklab.server.search;

public abstract class JobDescriptionBasic extends JobDescriptionAbstract {

	String indexName;

	public JobDescriptionBasic(String indexName) {
		this.indexName = indexName;
	}

	/**
	 * Get the index name
	 * @return name of the index this job if for
	 */
	@Override
	public String getIndexName() {
		return indexName;
	}


}
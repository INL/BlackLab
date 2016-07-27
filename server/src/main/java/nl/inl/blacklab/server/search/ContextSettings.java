package nl.inl.blacklab.server.search;

import nl.inl.blacklab.search.ConcordanceType;

public class ContextSettings {

	private int size;

	private ConcordanceType concType;

	public ContextSettings(int size, ConcordanceType concType) {
		super();
		this.size = size;
		this.concType = concType;
	}

	public int size() {
		return size;
	}

	public ConcordanceType concType() {
		return concType;
	}

	@Override
	public String toString() {
		return "ContextSettings [size=" + size + ", concType=" + concType + "]";
	}

}

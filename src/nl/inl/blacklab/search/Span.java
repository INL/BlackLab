package nl.inl.blacklab.search;

/**
 * A start and end position (no document id).
 */
public class Span {
	public int start, end;

	public Span(int start, int end) {
		this.start = start;
		this.end = end;
	}

	@Override
	public String toString() {
		return start + "-" + end;
	}

}

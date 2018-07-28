package nl.inl.blacklab.search;

/**
 * A start and end position (no document id).
 */
public class Span {
	private int start, end;

	public Span(int start, int end) {
		this.start = start;
		this.end = end;
	}

	public int getStart() {
		return start;
	}

	public int getEnd() {
		return end;
	}

	@Override
	public String toString() {
		return start + "-" + end;
	}

}

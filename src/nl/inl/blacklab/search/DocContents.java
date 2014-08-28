package nl.inl.blacklab.search;

/**
 * (Part of) the contents of a document.
 */
public abstract class DocContents {

	@Override
	public String toString() {
		return "DocContents: " + getStringContents();
	}

	public abstract String getStringContents();

}

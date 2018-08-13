package nl.inl.blacklab.search;

/**
 * (Part of) the contents of a document, in string form.
 *
 * Used as a base class for Concordance.
 */
public class DocContentsString extends DocContents {

    /** The content */
    String contents;

    /**
     * Construct DocContents object.
     *
     * @param contents the content
     */
    public DocContentsString(String contents) {
        this.contents = contents;
    }

    @Override
    public String xml() {
        return contents;
    }

}

package nl.inl.blacklab.search;

/** A task to perform on a document. */
public interface DocTask {
    void perform(Doc doc);
}
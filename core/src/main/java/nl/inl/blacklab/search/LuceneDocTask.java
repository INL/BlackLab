package nl.inl.blacklab.search;

import org.apache.lucene.document.Document;

/** A task to perform on a Lucene document. */
public interface LuceneDocTask {
    void perform(Document doc);
}
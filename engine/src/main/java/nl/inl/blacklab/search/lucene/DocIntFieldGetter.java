package nl.inl.blacklab.search.lucene;

public interface DocIntFieldGetter {

    /** An int field getter that just returns its parameter (for integrated forward index) */
    DocIntFieldGetter USE_DOC_ID = docId -> docId;

    int advance(int doc);
}

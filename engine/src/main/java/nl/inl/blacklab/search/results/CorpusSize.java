package nl.inl.blacklab.search.results;

public final class CorpusSize {

    public static final CorpusSize EMPTY = new CorpusSize(0, 0);

    public static CorpusSize get(long documents, long tokens) {
        return new CorpusSize(documents, tokens);
    }

    private long documents;

    private long tokens;

    private CorpusSize(long documents, long tokens) {
        super();
        this.documents = documents;
        this.tokens = tokens;
    }

    public long getDocuments() {
        return documents;
    }

    /**
     * Return the number of tokens in the corpus, or a negative value if unknown.
     *
     * @return number of tokens in the corpus, or a negative value if unknown
     */
    public long getTokens() {
        return tokens;
    }

    public boolean hasTokenCount() { return tokens >= 0; }
    public boolean hasDocumentCount() { return documents >= 0; }

    @Override
    public String toString() {
        return String.format("CorpusSize(%d docs, %d tokens)", documents, tokens);
    }

}
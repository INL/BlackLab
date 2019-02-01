package nl.inl.blacklab.search.results;

public final class CorpusSize {
    
    public static CorpusSize get(int documents, long tokens) {
        return new CorpusSize(documents, tokens);
    }
    
    private int documents;
    
    private long tokens;

    private CorpusSize(int documents, long tokens) {
        super();
        this.documents = documents;
        this.tokens = tokens;
    }

    public int getDocuments() {
        return documents;
    }

    public long getTokens() {
        return tokens;
    }
    
}
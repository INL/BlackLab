package nl.inl.blacklab.server.lib.results;

import nl.inl.blacklab.server.index.Index;

public class ResultIndexProgress {
    private long files;
    private long docs;
    private long tokens;
    private String documentFormat;
    private Index.IndexStatus indexStatus;

    ResultIndexProgress(long files, long docs, long tokens, String documentFormat,
            Index.IndexStatus indexStatus) {
        this.files = files;
        this.docs = docs;
        this.tokens = tokens;
        this.documentFormat = documentFormat;
        this.indexStatus = indexStatus;
    }

    public long getFiles() {
        return files;
    }

    public long getDocs() {
        return docs;
    }

    public long getTokens() {
        return tokens;
    }

    public String getDocumentFormat() {
        return documentFormat;
    }

    public Index.IndexStatus getIndexStatus() {
        return indexStatus;
    }
}

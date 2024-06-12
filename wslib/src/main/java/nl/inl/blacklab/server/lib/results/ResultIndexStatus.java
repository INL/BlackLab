package nl.inl.blacklab.server.lib.results;

import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.server.index.Index;

public class ResultIndexStatus {
    private final Index index;
    private final IndexMetadata metadata;
    private long files;
    private long docs;
    private long tokens;
    private String documentFormat;
    private Index.IndexStatus indexStatus;
    private boolean ownedBySomeoneElse;

    ResultIndexStatus(Index index, long files, long docs, long tokens, boolean ownedBySomeoneElse) {
        this.index = index;
        this.metadata = index.getIndexMetadata();
        this.files = files;
        this.docs = docs;
        this.tokens = tokens;
        this.documentFormat = metadata.documentFormat();
        this.indexStatus = index.getStatus();
        this.ownedBySomeoneElse = ownedBySomeoneElse;
    }

    public Index getIndex() {
        return index;
    }

    public IndexMetadata getMetadata() {
        return metadata;
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

    public boolean isOwnedBySomeoneElse() {
        return ownedBySomeoneElse;
    }
}

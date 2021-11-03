package nl.inl.blacklab.index;

import java.io.Closeable;
import java.io.IOException;

/**
 * A metadata fetcher can fetch the metadata for a document from some external
 * source (a file, the network, etc.) and add it to the Lucene document.
 */
abstract public class MetadataFetcher implements Closeable {

    public DocIndexer docIndexer;

    public MetadataFetcher(DocIndexer docIndexer) {
        this.docIndexer = docIndexer;
    }

    /**
     * Fetch the metadata for the document currently being indexed and add it to the
     * document as indexed fields.
     */
    abstract public void addMetadata();

    /**
     * Close the fetcher, releasing any resources it holds
     * 
     * @throws IOException if closing caused an error
     */
    @Override
    public void close() throws IOException {
        // Nothing, by default
    }

}

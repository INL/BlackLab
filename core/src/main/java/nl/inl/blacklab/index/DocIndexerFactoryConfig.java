package nl.inl.blacklab.index;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;

import nl.inl.blacklab.index.config.ConfigInputFormat;
import nl.inl.blacklab.index.config.DocIndexerConfig;

public class DocIndexerFactoryConfig implements DocIndexerFactory {
    private final ConfigInputFormat config;

    public DocIndexerFactoryConfig(ConfigInputFormat config) {
        this.config = config;
    }

    @Override
    public DocIndexerConfig get(Indexer indexer, String documentName, Reader reader) {
        DocIndexerConfig d = DocIndexerConfig.fromConfig(config);
        d.setIndexer(indexer);
        d.setDocumentName(documentName);
        d.setDocument(reader);
        return d;
    }

    @Override
    public DocIndexerConfig get(Indexer indexer, String documentName, InputStream is, Charset cs) {
        DocIndexerConfig d = DocIndexerConfig.fromConfig(config);
        d.setIndexer(indexer);
        d.setDocumentName(documentName);
        d.setDocument(is, cs);
        return d;
    }

    @Override
    public DocIndexerConfig get(Indexer indexer, String documentName, File f, Charset cs) throws FileNotFoundException {
        DocIndexerConfig d = DocIndexerConfig.fromConfig(config);
        d.setIndexer(indexer);
        d.setDocumentName(documentName);
        d.setDocument(f, cs);
        return d;
    }

    @Override
    public DocIndexerConfig get(Indexer indexer, String documentName, byte[] b, Charset cs) {
        DocIndexerConfig d = DocIndexerConfig.fromConfig(config);
        d.setIndexer(indexer);
        d.setDocumentName(documentName);
        d.setDocument(b, cs);
        return d;
    }

    @Override
    public ConfigInputFormat getConfig() {
        return config;
    }
}
package nl.inl.blacklab.index;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.indexers.config.DocIndexerConfig;
import nl.inl.blacklab.indexers.config.InputFormatReader;

/**
 * Description of a supported input format that is configuration-based.
 */
public class InputFormatWithConfig implements InputFormat {

    private ConfigInputFormat config;

    private final String formatIdentifier;

    private final File formatFile;

    private String errorMessage;

    public InputFormatWithConfig(ConfigInputFormat config) {
        assert config != null;
        this.config = config;
        this.formatIdentifier = config.getName();
        this.formatFile = null;
    }

    public InputFormatWithConfig(String formatIdentifier, File formatFile) {
        config = null;
        this.formatIdentifier = formatIdentifier;
        this.formatFile = formatFile;
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }

    public String getIdentifier() {
        return formatIdentifier;
    }

    public String getDisplayName() {
        return getConfig().getDisplayName();
    }

    public String getDescription() {
        return getConfig().getDescription();
    }

    public String getHelpUrl() {
        return getConfig().getHelpUrl();
    }

    public boolean isVisible() {
        return getConfig().isVisible();
    }

    public synchronized ConfigInputFormat getConfig() {
        if (config == null) {
            assert formatFile != null;

            try {
                config = new ConfigInputFormat(formatIdentifier);
                config.setReadFromFile(formatFile);
                InputFormatReader.read(formatFile, config);
                config.validate();
                return config;
            } catch (InvalidInputFormatConfig e) {
                errorMessage = e.getMessage();
                throw e;
            } catch (IOException e) {
                errorMessage = "Error reading input format config file " + formatFile + ": " + e.getMessage();
                throw new InvalidInputFormatConfig(errorMessage, e);
            }
        }
        return config;
    }

    @Override
    public DocIndexerConfig createDocIndexer(DocWriter indexer, String documentName, InputStream is,
            Charset cs) {
        DocIndexerConfig d = DocIndexerConfig.fromConfig(getConfig());
        d.setDocWriter(indexer);
        d.setDocumentName(documentName);
        d.setDocument(is, cs);
        return d;
    }

    @Override
    public DocIndexerConfig createDocIndexer(DocWriter indexer, String documentName, File f, Charset cs)
            throws FileNotFoundException {
        DocIndexerConfig d = DocIndexerConfig.fromConfig(getConfig());
        d.setDocWriter(indexer);
        d.setDocumentName(documentName);
        d.setDocument(f, cs);
        return d;
    }

    @Override
    public DocIndexerConfig createDocIndexer(DocWriter indexer, String documentName, byte[] b, Charset cs) {
        DocIndexerConfig d = DocIndexerConfig.fromConfig(getConfig());
        d.setDocWriter(indexer);
        d.setDocumentName(documentName);
        d.setDocument(b, cs);
        return d;
    }
}

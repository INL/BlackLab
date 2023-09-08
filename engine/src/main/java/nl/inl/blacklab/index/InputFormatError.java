package nl.inl.blacklab.index;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.indexers.config.DocIndexerConfig;

/**
 * Description of a supported input format that is configuration-based.
 */
public class InputFormatError implements InputFormat {

    private final String formatIdentifier;

    private final String errorMessage;

    public String getIdentifier() {
        return formatIdentifier;
    }

    public String getDisplayName() {
        return formatIdentifier + " (error)";
    }

    public String getDescription() {
        return "There was an error loading the format '" +
                getIdentifier() + "': " + getErrorMessage();
    }

    public String getHelpUrl() {
        return "";
    }

    public boolean isVisible() {
        return false;
    }

    public InputFormatError(String formatIdentifier, String errorMessage) {
        assert !StringUtils.isEmpty(formatIdentifier);
        this.formatIdentifier = formatIdentifier;
        this.errorMessage = errorMessage;
    }

    @Override
    public DocIndexerConfig createDocIndexer(DocWriter indexer, String documentName, InputStream is,
            Charset cs) {
        throw new BlackLabRuntimeException(getDescription());
    }

    @Override
    public DocIndexerConfig createDocIndexer(DocWriter indexer, String documentName, File f, Charset cs)
            throws FileNotFoundException {
        throw new BlackLabRuntimeException(getDescription());
    }

    @Override
    public DocIndexerConfig createDocIndexer(DocWriter indexer, String documentName, byte[] b, Charset cs) {
        throw new BlackLabRuntimeException(getDescription());
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}

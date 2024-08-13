package nl.inl.blacklab.index;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.util.FileReference;

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
    public DocIndexer createDocIndexer(DocWriter indexer, FileReference file) {
        throw new BlackLabRuntimeException(getDescription());
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return "(error with format " + getIdentifier() + ": " + getErrorMessage() + ")";
    }
}

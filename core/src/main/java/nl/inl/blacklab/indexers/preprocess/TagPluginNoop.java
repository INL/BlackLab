package nl.inl.blacklab.indexers.preprocess;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import nl.inl.blacklab.exceptions.PluginException;

public class TagPluginNoop implements TagPlugin {
    
    @Override
    public boolean needsConfig() {
        return false;
    }

    @Override
    public String getId() {
        return "noop";
    }

    @Override
    public String getDisplayName() {
        return "NO OP";
    }

    @Override
    public String getDescription() {
        return "Passes through data without parsing";
    }

    @Override
    public void init(Map<String, String> config) throws PluginException {
        // NO OP
    }

    @Override
    public String getInputFormat() {
        return "";
    }

    @Override
    public String getOutputFormatIdentifier() {
        return "";
    }

    @Override
    public String getOutputFileName(String inputFileName) {
        return inputFileName;
    }

    @Override
    public void perform(Reader reader, Writer writer) throws PluginException {
        try {
            IOUtils.copy(reader, writer);
        } catch (IOException e) {
            throw new PluginException(e);
        }
    }
}

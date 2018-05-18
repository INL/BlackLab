package nl.inl.blacklab.indexers.preprocess;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Optional;

import org.apache.commons.io.IOUtils;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class TagPluginNoop implements TagPlugin {

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
    public void init(Optional<ObjectNode> config) throws PluginException {
        return;
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

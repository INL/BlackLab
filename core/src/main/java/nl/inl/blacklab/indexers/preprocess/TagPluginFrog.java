package nl.inl.blacklab.indexers.preprocess;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class TagPluginFrog implements TagPlugin {
	private static final Logger logger = LogManager.getLogger(TagPluginFrog.class);

	@Override
	public String getId() {
		return "frog";
	}

	@Override
	public String getDisplayName() {
		return "frog";
	}

	@Override
	public String getDescription() {
		return "Folia tagger using the Frog library";
	}


	@Override
	public void init(ObjectNode config) throws PluginException {
		logger.info("initializing plugin " + getDisplayName());
	}

	@Override
	public void perform(Reader reader, Writer writer)
			throws PluginException {
		try {
			org.apache.commons.io.IOUtils.copy(reader, writer);
		} catch (IOException e) {
			throw new PluginException(e);
		}
	}

	@Override
	public String getInputFormat() {
		return "folia";
	}

	@Override
	public String getOutputFormatIdentifier() {
		return "folia";
	}

	@Override
	public String getOutputFileName(String inputFileName) {
		return FilenameUtils.removeExtension(inputFileName).concat(".xml");
	}
}

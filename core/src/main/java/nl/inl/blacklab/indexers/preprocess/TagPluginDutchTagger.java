package nl.inl.blacklab.indexers.preprocess;

import java.io.Reader;
import java.io.Writer;

import org.apache.commons.io.FilenameUtils;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class TagPluginDutchTagger implements TagPlugin {

	@Override
	public String getId() {
		return "DutchTagger";
	}

	@Override
	public String getDisplayName() {
		return "DutchTagger";
	}

	@Override
	public String getDescription() {
		return "";
	}

	@Override
	public void init(ObjectNode config) throws PluginException {
		if (config == null)
			throw new PluginException("This plugin requires configuration");


	}

	@Override
	public void perform(Reader reader, Writer writer) throws PluginException {
		// TODO Auto-generated method stub
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

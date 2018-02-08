package nl.inl.blacklab.indexers.preprocess;

import java.io.Reader;
import java.io.Writer;
import java.util.Set;

public interface ConvertPlugin extends Plugin {

	/**
	 * Get a list of file extensions or formats (not strictly formatIdentifiers, though there might be overlap) that this converter can convert from.
	 * Is purely for descriptive purposes, and has no exact relation to the strings passed into {@link ConvertPlugin#perform(Reader, String, Writer)},
	 * so exact spelling, punctuation or capitalization is not of technical importance.
	 *
	 * This overlaps somewhat with the purpose of getDescription() but it's important we have a consistent way of describing converter capabilties.
	 *
	 * @return the list of formats and file extensions supported by this converter.
	 */
	public Set<String> getInputFormats();

	/**
	 * Get the name of the format this plugin will convert files into.
	 * Contrary to {@link ConvertPlugin#getInputFormats()}, this format must match a format accepted by a {@link TagPlugin}.
	 * Should be as descriptive as possible (e.g. just "xml" is not exact enough,
	 * because xml can mean different things based on the contents of the file, so instead use "folia" or "tei", or whatever contents the file happens to have).
	 *
	 * @return the format this convertplugin outputs.
	 */
	public String getOutputFormat();

	/**
	 * Perform on a text file.
	 *
	 * @param reader input. Should not be closed by the implementation.
	 * @param inputFormat format of the input data, may also be a file extension
	 * @param writer output. Should not be closed by the implementation.
	 * @throws PluginException
	 */
	public void perform(Reader reader, String inputFormat, Writer writer) throws PluginException;
}

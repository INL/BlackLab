package nl.inl.blacklab.indexers.preprocess;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.nio.charset.Charset;
import java.util.Set;

import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.index.Plugin;

public interface ConvertPlugin extends Plugin {

    /**
     * Get a list of file extensions or formats (not strictly formatIdentifiers,
     * though there might be overlap) that this converter can convert from. Is
     * purely for descriptive purposes, and has no exact relation to the strings
     * passed into
     * {@link ConvertPlugin#perform(InputStream, Charset, String, OutputStream)}, so
     * exact spelling, punctuation or capitalization is not of technical importance.
     *
     * This overlaps somewhat with the purpose of getDescription() but it's
     * important we have a consistent way of describing converter capabilties.
     *
     * @return the list of formats and file extensions supported by this converter.
     */
    Set<String> getInputFormats();

    /**
     * Get the name of the format this plugin will convert files into. Contrary to
     * {@link ConvertPlugin#getInputFormats()}, this format must match a format
     * accepted by a {@link TagPlugin}. Should be as descriptive as possible (e.g.
     * just "xml" is not exact enough, because xml can mean different things based
     * on the contents of the file, so instead use "folia" or "tei", or whatever
     * contents the file happens to have).
     *
     * @return the format this convertplugin outputs.
     */
    String getOutputFormat();

    /**
     * Can this converter convert this file
     *
     * @param is stream containing a pushback buffer of at least 251 characters
     * @param cs (optional) charset of the inputstream, if this is a text
     *            (non-binary) file type
     * @param inputFormat
     * @return true if this file can be converted into this plugin's outputFormat
     */
    boolean canConvert(PushbackInputStream is, Charset cs, String inputFormat);

    /**
     * Perform on a text file.
     *
     * @param is input. Should not be closed by the implementation.
     * @param cs as inputFormat, but for the charset of the inputStream, not always
     *            meaningful, but required for some implementations that transform
     *            textual data.
     * @param inputFormat arbitrary string describing input data, can be file
     *            extension, or more semantic. Usage may vary, so acceptable values
     *            must be coordinated between callers and implementations.
     * @param os output. Should not be closed by the implementation.
     * @throws PluginException
     */
    void perform(InputStream is, Charset cs, String inputFormat, OutputStream os) throws PluginException;
}

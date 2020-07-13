package nl.inl.blacklab.indexers.preprocess;

import java.io.Reader;
import java.io.Writer;

import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.index.DocIndexer;
import nl.inl.blacklab.index.Plugin;

public interface TagPlugin extends Plugin {

    /**
     * Get the name of the format this plugin can tag. Should be as descriptive as
     * possible (e.g. just "xml" is not exact enough, because xml can mean different
     * things based on the contents of the file, so instead use "folia" or "tei", or
     * whatever contents the file happens to have). see
     * {@link ConvertPlugin#getOutputFormat()}.
     *
     * @return the format this convertplugin outputs.
     */
    String getInputFormat();

    /**
     * Get the name of the format this plugin tags into. This should match a
     * {@link nl.inl.blacklab.index.DocIndexerFactory.Format} so that the tagged
     * data can then be indexed by a {@link DocIndexer}
     *
     * @return the formatIdentifier
     */
    String getOutputFormatIdentifier();

    /**
     * Unfortunate side-effect of docIndexers requiring a filename to do their work.
     * 
     * @param inputFileName
     * @return a valid file name for an indexable file of the type as returned by
     *         {@link TagPlugin#getOutputFormatIdentifier()}
     */
    String getOutputFileName(String inputFileName);

    /**
     * Perform on a text file.
     *
     * @param reader input. Should not be closed by the implementation.
     * @param writer output. Should not be closed by the implementation.
     * @throws PluginException
     */
    void perform(Reader reader, Writer writer) throws PluginException;
}

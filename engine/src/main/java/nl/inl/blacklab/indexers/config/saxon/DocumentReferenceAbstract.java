package nl.inl.blacklab.indexers.config.saxon;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.function.Supplier;

import org.apache.commons.io.IOUtils;

import nl.inl.util.TextContent;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

/** A way to access the contents of a document.
 *
 * Contents may be stored in memory for smaller documents, or be read from disk for larger ones.
 */
public abstract class DocumentReferenceAbstract implements DocumentReference {

    /** Helper for resolving XIncludes */
    Supplier<Reader> xincludeResolver;

    /** Internal Reader for getTextContent(). */
    private Reader internalReader = null;

    /** Where our internal reader is positioned currently (next char to be read). */
    private long internalReaderOffset = -1;

    /**
     * Read characters from a Reader, starting at startOffset and ending at endOffset.
     *
     * @param reader the Reader to read from
     * @param startOffset the offset to start reading at
     * @param endOffset the offset to stop reading at, or -1 to read until the end
     * @return the characters read
     * @throws IOException
     */
    private static char[] readerToCharArray(Reader reader, long startOffset, long endOffset) {
        try {
            if (startOffset > 0)
                IOUtils.skip(reader, startOffset);
            if (endOffset != -1) {
                int length = (int)(endOffset - startOffset);
                char[] result = new char[length];
                if (reader.read(result, 0, length) < 0)
                    throw new RuntimeException("Unexpected end of file");
                return result;
            } else {
                return IOUtils.toCharArray(reader);
            }
        } catch (IOException e) {
            throw new BlackLabRuntimeException(e);
        }
    }

    /**
     * Get part of the document as a TextContent object.
     * @param startOffset the offset to start reading at
     * @param endOffset the offset to stop reading at, or -1 to read until the end
     * @return the content read
     */
    @Override
    public TextContent getTextContent(long startOffset, long endOffset) {
        Reader reader = getInternalReader(startOffset);
        // Read characters starting startOffset and ending at endOffset
        char[] result = readerToCharArray(reader, 0, endOffset - startOffset);
        return new TextContent(result);
    }

    @Override
    public void clean() {
        xincludeResolver = null;
        if (internalReader != null) {
            try {
                internalReader.close();
            } catch (IOException e) {
                throw new BlackLabRuntimeException(e);
            }
            internalReader = null;
        }
    }

    /**
     * Set directory for resolving XIncludes in the document.
     *
     * Activates the resolving for xi:include elements, which is disabled by default.
     *
     * @param dir the directory to resolve relative XInclude paths against
     * @return document reference with XIncludes resolved (may be the same or a new instance)
     */
    @Override
    public void setXIncludeDirectory(File dir) {
        xincludeResolver = new XIncludeResolver(getBaseDocReaderSupplier(), dir);
    }

    public abstract Supplier<Reader> getBaseDocReaderSupplier();

    Reader getInternalReader(long startOffset) {
        if (internalReader == null || internalReaderOffset > startOffset) {
            internalReader = getDocumentReader();
            internalReaderOffset = 0;
        }
        try {
            internalReaderOffset += internalReader.skip(startOffset - internalReaderOffset);
        } catch (IOException e) {
            throw new BlackLabRuntimeException(e);
        }
        if (internalReaderOffset < startOffset) {
            throw new BlackLabRuntimeException("Could not skip to start offset");
        }
        return internalReader;
    }

    @Override
    public Reader getDocumentReader() {
        if (xincludeResolver == null) {
            // No resolver configured; just return a reader for the base document
            xincludeResolver = getBaseDocReaderSupplier();
        }
        return xincludeResolver.get();
    }

}

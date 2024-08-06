package nl.inl.blacklab.indexers.config.saxon;

import java.io.File;
import java.io.IOException;
import java.io.Reader;

import nl.inl.blacklab.contentstore.TextContent;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

/** A way to access the contents of a document.
 *
 * Contents may be stored in memory for smaller documents, or be read from disk for larger ones.
 */
public abstract class DocumentReferenceAbstract implements DocumentReference {

    /** Helper for resolving XIncludes */
    XIncludeResolver xincludeResolver;

    /**
     * Get part of the document as a TextContent object.
     * @param startOffset the offset to start reading at
     * @param endOffset the offset to stop reading at, or -1 to read until the end
     * @return the content read
     */
    @Override
    public TextContent getTextContent(long startOffset, long endOffset) {
        return new TextContent(getCharArray(startOffset, endOffset));
    }

    /**
     * Get part of the document as a char array.
     * @param startOffset the offset to start reading at
     * @param endOffset the offset to stop reading at, or -1 to read until the end
     * @return the characters read
     */
    char[] getCharArray(long startOffset, long endOffset) {
        // Read from the Reader provided by the XIncludeResolver
        try (Reader reader = getDocumentReader()) {
            // Read characters starting startOffset and ending at endOffset
            return DocumentReference.readerToCharArray(reader, startOffset, endOffset);
        } catch (IOException e) {
            throw new BlackLabRuntimeException(e);
        }
    }

    @Override
    public void clean() {
        xincludeResolver = null;
    }

    /**
     * Set directory for resolving XIncludes in the document.
     *
     * Activates the resolving for xi:include elements, which is disabled by default.
     *
     * @param currentXIncludeDir the directory to resolve relative XInclude paths against
     * @return document reference with XIncludes resolved (may be the same or a new instance)
     */
    @Override
    public void setXIncludeDirectory(File currentXIncludeDir) {
        // XInclude resolver that uses separate Readers for each part
        xincludeResolver = new XIncludeResolverSeparate(getBaseDocument(), currentXIncludeDir);
    }

    abstract char[] getBaseDocument();

    @Override
    public Reader getDocumentReader() {
        return getXIncludeResolver().getDocumentReader();
    }

    XIncludeResolver getXIncludeResolver() {
        if (xincludeResolver == null) {
            xincludeResolver = getDummyXIncludeResolver();
        }
        return xincludeResolver;
    }

    abstract XIncludeResolver getDummyXIncludeResolver();
}

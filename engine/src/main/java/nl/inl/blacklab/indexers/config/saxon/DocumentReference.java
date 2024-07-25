package nl.inl.blacklab.indexers.config.saxon;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

import nl.inl.blacklab.contentstore.TextContent;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

/** A way to access the contents of a document from memory or, for large documents, from disk. */
public class DocumentReference {

    /** If doc is larger than this, save it to a temporary file and read it back later. */
    private static final int MAX_DOC_SIZE_IN_MEMORY_BYTES = 4_096_000;

    private static final boolean USE_IMPROVED_XINCLUDE_RESOLVER = true;

    /**
     * The document as a string, will be used for storing document and position calculation.
     * (for large document, may be null after init)
     */
    private char[] contents;

    /** Charset used by the file */
    private Charset fileCharset;

    /**
     * If we were called with a file, we'll store it here.
     * Large files also get temporarily stored on disk until they're needed again.
     */
    private File file;

    /**
     * If we created a temporary file, we'll delete it on exit.
     */
    private boolean deleteFileOnExit = false;

    /** Helper for resolving XIncludes */
    private XIncludeResolver xincludeResolver;

    private boolean useContentFromXIncludeResolver = false;

    public DocumentReference(char[] contents, Charset fileCharset, File file, boolean swapIfTooLarge) {
        this(contents, fileCharset, file, swapIfTooLarge, null);
    }

    public DocumentReference(char[] contents, Charset fileCharset, File file, boolean swapIfTooLarge, XIncludeResolver xIncludeResolver) {
        this.contents = contents;
        this.fileCharset = fileCharset;
        this.file = file;
        if (swapIfTooLarge)
            swapIfTooLarge();
        this.xincludeResolver = xIncludeResolver;
    }

    /**
     * Swap the contents to a file if it's too large.
     */
    void swapIfTooLarge() {
        if (contents != null && contents.length * Character.BYTES > MAX_DOC_SIZE_IN_MEMORY_BYTES) {
            if (file == null) {
                // We don't have a file with the contents yet; create it now.
                try {
                    file = File.createTempFile("blDocToIndex", null);
                    file.deleteOnExit();
                    deleteFileOnExit = true;
                    fileCharset = StandardCharsets.UTF_8;
                    try (FileWriter writer = new FileWriter(file, fileCharset)) {
                        IOUtils.write(contents, writer);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Error swapping large doc to disk", e);
                }
            }
            contents = null; // drop the contents from memory
        }
    }

    /**
     * Get the document as a TextContent object.
     * @return the content read
     */
    public TextContent getTextContent() {
        return new TextContent(getCharArray());
    }

    /**
     * Get part of the document as a TextContent object.
     * @param startOffset the offset to start reading at
     * @param endOffset the offset to stop reading at, or -1 to read until the end
     * @return the content read
     */
    public TextContent getTextContent(long startOffset, long endOffset) {
        return new TextContent(getCharArray(startOffset, endOffset));
    }

    /**
     * Get the document as a char array.
     * @return the characters read
     */
    public char[] getCharArray() {
        return getCharArray(0, -1);
    }

    /**
     * Read characters from a Reader, starting at startOffset and ending at endOffset.
     *
     * @param reader the Reader to read from
     * @param startOffset the offset to start reading at
     * @param endOffset the offset to stop reading at, or -1 to read until the end
     * @return the characters read
     * @throws IOException
     */
    private static char[] toCharArrayPart(Reader reader, long startOffset, long endOffset) throws IOException {
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
    }

    /**
     * Get part of the document as a char array.
     * @param startOffset the offset to start reading at
     * @param endOffset the offset to stop reading at, or -1 to read until the end
     * @return the characters read
     */
    public char[] getCharArray(long startOffset, long endOffset) {
        if (useContentFromXIncludeResolver && xincludeResolver != null) {
            // Read from the Reader provided by the XIncludeResolver
            try (Reader reader = xincludeResolver.getDocumentReader()) {
                // Read characters starting startOffset and ending at endOffset
                return toCharArrayPart(reader, startOffset, endOffset);
            } catch (IOException e) {
                throw new BlackLabRuntimeException(e);
            }
        } else {
            return getDocWithoutXIncludesResolved(startOffset, endOffset);
        }
    }

    /**
     * Get the original XML doc before resolving XIncludes.
     * @return the characters read
     */
    public char[] getDocWithoutXIncludesResolved() {
        return getDocWithoutXIncludesResolved(0, -1);
    }

    /**
     * Get part of the original XML doc before resolving XIncludes.
     *
     * @param startOffset the offset to start reading at
     * @param endOffset the offset to stop reading at, or -1 to read until the end
     * @return the characters read
     */
    public char[] getDocWithoutXIncludesResolved(long startOffset, long endOffset) {
        if (contents == null) {
            // Read from file
            try {
                try (FileReader reader = new FileReader(file, fileCharset)) {
                    return toCharArrayPart(reader, startOffset, endOffset);
                }
            } catch (IOException e) {
                throw new BlackLabRuntimeException(e);
            }
        }
        return contents;
    }

    public void clean() {
        if (file != null && deleteFileOnExit)
            file.delete();
        contents = null;
        file = null;
        xincludeResolver = null;
        useContentFromXIncludeResolver = false;
    }

    /**
     * Resolve XIncludes in the document.
     *
     * @param currentXIncludeDir the directory to resolve relative XInclude paths against
     * @return document reference with XIncludes resolved (may be the same or a new instance)
     */
    public DocumentReference withXIncludesResolved(File currentXIncludeDir) {
        if (USE_IMPROVED_XINCLUDE_RESOLVER) {
            // Improved XInclude resolver that uses separate Readers for each part
            this.xincludeResolver = new XIncludeResolverSeparate(this, currentXIncludeDir);
            this.useContentFromXIncludeResolver = xincludeResolver.anyXIncludesFound();
            return this;
        } else {
            // Naive XInclude resolver that just concatenates the parts together to a huge string
            this.xincludeResolver = new XIncludeResolverConcatenate(this, currentXIncludeDir);
            return xincludeResolver.getDocumentReference(); // in case there were XIncludes
        }
    }

    public XIncludeResolver getXIncludeResolver() {
        return xincludeResolver;
    }

    /**
     * Return a new instance of this DocumentReference with resolved documentContent and the XIncludeResolver set.
     * @param documentContent the resolved document content
     * @param xIncludeResolverConcatenate the XIncludeResolver to use
     * @return the new instance
     */
    DocumentReference withXIncludeResolver(char[] documentContent, XIncludeResolverConcatenate xIncludeResolverConcatenate) {
        return new DocumentReference(documentContent, fileCharset, file, true, xIncludeResolverConcatenate);
    }

    public CharPositionsTracker getCharPositionsTracker() {
        assert xincludeResolver != null: "Resolve XIncludes first";
        return xincludeResolver.getCharPositionsTracker();
    }

    public Reader getDocumentReader() {
        assert xincludeResolver != null: "Resolve XIncludes first";
        return xincludeResolver.getDocumentReader();
    }
}

package nl.inl.blacklab.indexers.config.saxon;

import java.io.CharArrayReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolve xi:includes using a composite Reader.
 *
 * Uses a lot less memory because the contents aren't kept in-memory.
 */
public class XIncludeResolverSeparate implements XIncludeResolver {

    private final DocumentReference document;

    private final File baseDir;

    private final CharPositionsTrackerImpl charPositionsTracker;

    private boolean anyXIncludesFound = false;

    public XIncludeResolverSeparate(DocumentReference document, File curDir) {
        this.document = document;
        this.baseDir = curDir;

        // Read through the document(s) once, capturing the character positions for each tag.
        try (Reader reader = getDocumentReader()) {
            charPositionsTracker = new CharPositionsTrackerImpl(reader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DocumentReference getDocumentReference() {
        return document;
    }

    @Override
    public Reader getDocumentReader() {
        // Find XInclude tags and construct a list of readers
        List<Reader> readers = findXIncludesAndConstructReaderList(document.getDocWithoutXIncludesResolved(), baseDir);

        return new Reader() {
            @Override
            public int read(char[] chars, int offset, int length) throws IOException {
                if (length == 0)
                    return 0;
                int totalRead = 0;
                while (!readers.isEmpty() && length > 0) {
                    Reader currentReader = readers.get(0);
                    int read = currentReader.read(chars, offset, length);
                    if (read == -1) {
                        // No chars read, end of reader
                        currentReader.close();
                        readers.remove(0);
                    } else {
                        // Some chars read; update variables
                        offset += read;
                        length -= read;
                        totalRead += read;
                    }
                }
                return totalRead == 0 ? -1 : totalRead;
            }

            @Override
            public void close() throws IOException {
                for (Reader reader: readers) {
                    reader.close();
                }
            }
        };
    }

    @Override
    public CharPositionsTracker getCharPositionsTracker() {
        return charPositionsTracker;
    }

    private List<Reader> findXIncludesAndConstructReaderList(char[] documentContent, File dir) {
        // Implement XInclude support.
        // We need to do this before parsing so our character position tracking keeps working.
        // This basic support uses regex; we can improve it later if needed.
        // <xi:include href="../content/en_1890_Darby.1Chr.xml"/>
        Pattern xIncludeTag = Pattern.compile("<xi:include\\s+href=\"([^\"]+)\"\\s*/>");
        CharSequence doc = CharBuffer.wrap(documentContent);
        Matcher matcher = xIncludeTag.matcher(doc);
        List<Reader> readers = new ArrayList<>();
        int pos = 0;
        anyXIncludesFound = false;
        while (matcher.find()) {
            anyXIncludesFound = true;
            // The part before the XInclude tag
            if (pos < matcher.start())
                readers.add(new CharArrayReader(documentContent, pos, matcher.start() - pos));
            pos = matcher.end();

            // The included file
            String href = matcher.group(1);
            File f = new File(href);
            if (!f.isAbsolute())
                f = new File(dir, href);
            try {
                readers.add(new FileReader(f));
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        // The rest of the document
        if (pos < documentContent.length)
            readers.add(new CharArrayReader(documentContent, pos, documentContent.length - pos));
        return readers;
    }

    @Override
    public boolean anyXIncludesFound() {
        return anyXIncludesFound;
    }
}

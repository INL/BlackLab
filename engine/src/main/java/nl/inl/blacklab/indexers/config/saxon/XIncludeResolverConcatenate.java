package nl.inl.blacklab.indexers.config.saxon;

import java.io.CharArrayReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

public class XIncludeResolverConcatenate implements XIncludeResolver {

    private final DocumentReference documentReference;

    private final CharArrayReader documentReader;

    private final CharPositionsTracker charPositions;

    public XIncludeResolverConcatenate(DocumentReference documentReference, File curDir) {
        File file = documentReference.getFile();
        Charset charset = documentReference.getCharset();
        char[] documentContent = documentReference.getContents();
        File baseDir = file == null ? curDir : file.getParentFile();
        char[] documentContentNew = resolveXInclude(documentContent, baseDir);
        if (documentContentNew != documentContent) {
            documentContent = documentContentNew;
            this.documentReference = new DocumentReference(documentContent, charset, file, true, this);
        } else {
            this.documentReference = documentReference;
        }
        this.charPositions = new CharPositionsTrackerImpl(documentContent);
        this.documentReader = new CharArrayReader(documentContent);
    }

    @Override
    public DocumentReference getDocumentReference() {
        return documentReference;
    }

    @Override
    public Reader getDocumentReader() {
        return documentReader;
    }

    @Override
    public CharPositionsTracker getCharPositionsTracker() {
        return charPositions;
    }

    private char[] resolveXInclude(char[] documentContent, File dir) {
        // Implement XInclude support.
        // We need to do this before parsing so our character position tracking keeps working.
        // This basic support uses regex; we can improve it later if needed.
        // <xi:include href="../content/en_1890_Darby.1Chr.xml"/>

        Pattern xIncludeTag = Pattern.compile("<xi:include\\s+href=\"([^\"]+)\"\\s*/>");
        CharSequence doc = CharBuffer.wrap(documentContent);
        Matcher matcher = xIncludeTag.matcher(doc);
        StringBuilder result = new StringBuilder(documentContent.length / 2 * 3);
        int pos = 0;
        boolean anyFound = false;
        while (matcher.find()) {
            anyFound = true;
            // Append the part before the XInclude tag
            result.append(doc.subSequence(pos, matcher.start()));
            try {
                // Append the included file
                String href = matcher.group(1);
                File f = new File(href);
                if (!f.isAbsolute())
                    f = new File(dir, href);
                InputStream is = new FileInputStream(f);
                result.append(IOUtils.toString(is, StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw BlackLabRuntimeException.wrap(e);
            }
            pos = matcher.end();
        }
        if (!anyFound)
            return documentContent;
        // Append the rest of the document
        result.append(doc.subSequence(pos, doc.length()));
        return result.toString().toCharArray();
    }
}

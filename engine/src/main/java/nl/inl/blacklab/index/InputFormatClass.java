package nl.inl.blacklab.index;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.util.UnicodeStream;

/**
 * Description of a supported input format that is not configuration-based.
 */
public class InputFormatClass implements InputFormat {

    protected final String formatIdentifier;

    private final Class<? extends DocIndexerLegacy> docIndexerClass;

    public String getIdentifier() {
        return formatIdentifier;
    }

    public String getDisplayName() {
        return DocIndexerLegacy.getDisplayName(docIndexerClass);
    }

    public String getDescription() {
        return DocIndexerLegacy.getDescription(docIndexerClass);
    }

    public String getHelpUrl() {
        return "";
    }

    public boolean isVisible() {
        return DocIndexerLegacy.isVisible(docIndexerClass);
    }

    public Class<? extends DocIndexerLegacy> getDocIndexerClass() {
        return docIndexerClass;
    }

    public InputFormatClass(String formatIdentifier, Class<? extends DocIndexerLegacy> docIndexerClass) {
        assert !StringUtils.isEmpty(formatIdentifier);
        this.formatIdentifier = formatIdentifier;
        this.docIndexerClass = docIndexerClass;
    }

    @Override
    public DocIndexer createDocIndexer(DocWriter indexer, String documentName, InputStream is, Charset cs) {
        try {
            // Instantiate our DocIndexer class
            Constructor<? extends DocIndexer> constructor;
            DocIndexer docIndexer;
            try {
                constructor = docIndexerClass.getConstructor();
                docIndexer = constructor.newInstance();
                docIndexer.setDocWriter(indexer);
                docIndexer.setDocumentName(documentName);
                docIndexer.setDocument(is, cs);
            } catch (NoSuchMethodException e) {
                // No, this is an older DocIndexer that takes document name and reader directly.
                constructor = docIndexerClass.getConstructor(DocWriter.class, String.class, Reader.class);
                docIndexer = constructor.newInstance(indexer, documentName, new InputStreamReader(is, cs));
            }
            return docIndexer;
        } catch (SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException
                 | InvocationTargetException | NoSuchMethodException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public DocIndexer createDocIndexer(DocWriter indexer, String documentName, File f, Charset cs) {
        try {
            // Instantiate our DocIndexer class
            Constructor<? extends DocIndexer> constructor;
            DocIndexer docIndexer;
            try {
                constructor = docIndexerClass.getConstructor();
                docIndexer = constructor.newInstance();
                docIndexer.setDocWriter(indexer);
                docIndexer.setDocumentName(documentName);
                docIndexer.setDocument(f, cs);
            } catch (NoSuchMethodException e) {
                // No, this is an older DocIndexer that takes document name and reader directly.
                constructor = docIndexerClass.getConstructor(DocWriter.class, String.class, Reader.class);
                UnicodeStream is = new UnicodeStream(new FileInputStream(f), Indexer.DEFAULT_INPUT_ENCODING);
                Charset detectedCharset = is.getEncoding();
                docIndexer = constructor.newInstance(indexer, documentName, new InputStreamReader(is, detectedCharset));
            }
            return docIndexer;
        } catch (SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException
                 | InvocationTargetException | NoSuchMethodException | IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public DocIndexer createDocIndexer(DocWriter indexer, String documentName, byte[] contents, Charset cs) {
        try {
            // Instantiate our DocIndexer class
            Constructor<? extends DocIndexer> constructor;
            DocIndexer docIndexer;
            try {
                constructor = docIndexerClass.getConstructor();
                docIndexer = constructor.newInstance();
                docIndexer.setDocWriter(indexer);
                docIndexer.setDocumentName(documentName);
                docIndexer.setDocument(contents, cs);
            } catch (NoSuchMethodException e) {
                // No, this is an older DocIndexer that takes document name and reader directly.
                constructor = docIndexerClass.getConstructor(DocWriter.class, String.class, Reader.class);
                docIndexer = constructor.newInstance(indexer, documentName,
                        new InputStreamReader(new ByteArrayInputStream(contents), cs));
            }
            return docIndexer;
        } catch (SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException
                 | InvocationTargetException | NoSuchMethodException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public String toString() {
        return "class-based input format '" + formatIdentifier +
                "' from class " + docIndexerClass.getCanonicalName();
    }
}

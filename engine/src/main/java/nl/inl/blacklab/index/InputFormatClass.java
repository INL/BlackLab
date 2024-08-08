package nl.inl.blacklab.index;

import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.util.FileReference;

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
    public DocIndexer createDocIndexer(DocWriter indexer, FileReference file) {
        try {
            // Instantiate our DocIndexer class
            Constructor<? extends DocIndexer> constructor;
            DocIndexer docIndexer;
            try {
                constructor = docIndexerClass.getConstructor();
                docIndexer = constructor.newInstance();
                docIndexer.setDocWriter(indexer);
                docIndexer.setDocumentName(file.getPath());
                docIndexer.setDocument(file);
            } catch (NoSuchMethodException e) {
                // No, this is an older DocIndexer that takes document name and reader directly.
                constructor = docIndexerClass.getConstructor(DocWriter.class, String.class, Reader.class);
                InputStreamReader inputStreamReader = new InputStreamReader(file.getSinglePassInputStream(), file.getCharSet());
                docIndexer = constructor.newInstance(indexer, file.getPath(), inputStreamReader);
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

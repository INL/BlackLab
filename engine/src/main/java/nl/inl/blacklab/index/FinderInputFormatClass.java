package nl.inl.blacklab.index;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

/**
 * Supports creation of several types of DocIndexers implemented directly in
 * code. Additionally will attempt to load classes if passed a fully-qualified
 * ClassName, and implementations by name in .indexers package within BlackLab.
 */
public class FinderInputFormatClass implements FinderInputFormat {

    @Override
    public InputFormat find(String formatIdentifier) {
        // Is it a fully qualified class name?
        Class<? extends DocIndexerLegacy> docIndexerClass = null;
        try {
            docIndexerClass = getDocIndexerClass(formatIdentifier);
        } catch (Exception e1) {
            try {
                // No. Is it a class in the BlackLab indexers package?
                docIndexerClass = getDocIndexerClass("nl.inl.blacklab.indexers." + formatIdentifier);
            } catch (Exception e) {
                // Couldn't be resolved. That's okay, maybe another factory will support this key.
            }
        }
        if (docIndexerClass != null) {
            return DocumentFormats.add(formatIdentifier, docIndexerClass);
        }
        return null;
    }

    private static Class<? extends DocIndexerLegacy> getDocIndexerClass(String formatIdentifier) throws ClassNotFoundException {
        Class<?> aClass = Class.forName(formatIdentifier);
        if (!DocIndexerLegacy.class.isAssignableFrom(aClass)) {
            throw new BlackLabRuntimeException("Class " + formatIdentifier + " is not a DocIndexer");
        }
        return (Class<? extends DocIndexerLegacy>) aClass;
    }

}

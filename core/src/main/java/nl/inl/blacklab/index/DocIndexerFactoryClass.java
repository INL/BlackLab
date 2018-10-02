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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.indexers.DocIndexerAlto;
import nl.inl.blacklab.indexers.DocIndexerFolia;
import nl.inl.blacklab.indexers.DocIndexerPageXml;
import nl.inl.blacklab.indexers.DocIndexerTei;
import nl.inl.blacklab.indexers.DocIndexerTeiPosInFunctionAttr;
import nl.inl.blacklab.indexers.DocIndexerTeiText;
import nl.inl.blacklab.indexers.DocIndexerWhiteLab2;
import nl.inl.blacklab.indexers.DocIndexerXmlSketch;
import nl.inl.util.UnicodeStream;

/**
 * Supports creation of several types of DocIndexers implemented directly in
 * code. Additionally will attempt to load classes if passed a fully-qualified
 * ClassName, and implementations by name in .indexers package within BlackLab.
 */
public class DocIndexerFactoryClass implements DocIndexerFactory {

    private Map<String, Class<? extends DocIndexerAbstract>> supported = new HashMap<>();
    private Set<String> unsupported = new HashSet<>();

    @Override
    public void init() {
        // Note that these names should not collide with the builtin config-based formats, or those will be used instead.

        // Some abbreviations for commonly used builtin DocIndexers.
        // You can also specify the classname for builtin DocIndexers,
        // or a fully-qualified name for your custom DocIndexer (must
        // be on the classpath)
        supported.put("alto", DocIndexerAlto.class);
        supported.put("di-folia", DocIndexerFolia.class);
        supported.put("whitelab2", DocIndexerWhiteLab2.class);
        supported.put("pagexml", DocIndexerPageXml.class);
        supported.put("sketchxml", DocIndexerXmlSketch.class);

        // TEI has a number of variants
        // By default, the contents of the "body" element are indexed, but alternatively you can index the contents of "text".
        // By default, the "type" attribute is assumed to contain PoS, but alternatively you can use the "function" attribute.
        supported.put("di-tei", DocIndexerTei.class);
        supported.put("di-tei-element-text", DocIndexerTeiText.class);
        supported.put("di-tei-pos-function", DocIndexerTeiPosInFunctionAttr.class);
    }

    @Override
    public boolean isSupported(String formatIdentifier) {
        if (!supported.containsKey(formatIdentifier) && !unsupported.contains(formatIdentifier))
            find(formatIdentifier);

        return supported.containsKey(formatIdentifier);
    }

    @Override
    public List<Format> getFormats() {
        List<Format> ret = new ArrayList<>();
        for (Entry<String, Class<? extends DocIndexerAbstract>> e : supported.entrySet()) {
            Format desc = new Format(e.getKey(), DocIndexerAbstract.getDisplayName(e.getValue()),
                    DocIndexerAbstract.getDescription(e.getValue()));
            desc.setVisible(DocIndexerAbstract.isVisible(e.getValue()));
            ret.add(desc);
        }
        return ret;
    }

    @Override
    public Format getFormat(String formatIdentifier) {
        if (!isSupported(formatIdentifier))
            return null;

        Class<? extends DocIndexer> docIndexerClass = supported.get(formatIdentifier);
        Format desc = new Format(formatIdentifier, DocIndexerAbstract.getDisplayName(docIndexerClass),
                DocIndexerAbstract.getDescription(docIndexerClass));
        desc.setVisible(DocIndexerAbstract.isVisible(docIndexerClass));
        return desc;
    }

    public void addFormat(String formatIdentifier, Class<? extends DocIndexerAbstract> docIndexerClass) {
        this.supported.put(formatIdentifier, docIndexerClass);
    }

    @SuppressWarnings("unchecked")
    private void find(String formatIdentifier) {
        // Is it a fully qualified class name?
        Class<? extends DocIndexerAbstract> docIndexerClass = null;
        try {
            docIndexerClass = (Class<? extends DocIndexerAbstract>) Class.forName(formatIdentifier);
        } catch (Exception e1) {
            try {
                // No. Is it a class in the BlackLab indexers package?
                docIndexerClass = (Class<? extends DocIndexerAbstract>) Class
                        .forName("nl.inl.blacklab.indexers." + formatIdentifier);
            } catch (Exception e) {
                // Couldn't be resolved. That's okay, maybe another factory will support this key
                // Cache the key for next time.
                unsupported.add(formatIdentifier);
            }
        }

        if (docIndexerClass != null) {
            supported.put(formatIdentifier, docIndexerClass);
        }
    }

    @Override
    public DocIndexer get(String formatIdentifier, DocWriter indexer, String documentName, Reader reader) {
        if (!isSupported(formatIdentifier))
            throw new UnsupportedOperationException("Unknown format '" + formatIdentifier
                    + "', call isSupported(formatIdentifier) before attempting to get()");

        try {
            // Instantiate our DocIndexer class
            Class<? extends DocIndexer> docIndexerClass = supported.get(formatIdentifier);
            Constructor<? extends DocIndexer> constructor;
            DocIndexer docIndexer;
            try {
                // NOTE: newer DocIndexers have a constructor that takes only a DocWriter, not the document
                // being indexed. This allows us more flexibility in how we supply the document to this object
                // (e.g. as a file, a byte array, a reader, ...).
                // Assume this is a newer DocIndexer, and only construct it the old way if this fails.
                constructor = docIndexerClass.getConstructor();
                docIndexer = constructor.newInstance();
                docIndexer.setDocWriter(indexer);
                docIndexer.setDocumentName(documentName);
                docIndexer.setDocument(reader);
            } catch (NoSuchMethodException e) {
                // No, this is an older DocIndexer that takes document name and reader directly.
                constructor = docIndexerClass.getConstructor(DocWriter.class, String.class, Reader.class);
                docIndexer = constructor.newInstance(indexer, documentName, reader);
            }
            return docIndexer;
        } catch (SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException | NoSuchMethodException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public DocIndexer get(String formatIdentifier, DocWriter indexer, String documentName, InputStream is, Charset cs) {
        if (!isSupported(formatIdentifier))
            throw new UnsupportedOperationException("Unknown format '" + formatIdentifier
                    + "', call isSupported(formatIdentifier) before attempting to get()");

        try {
            // Instantiate our DocIndexer class
            Class<? extends DocIndexer> docIndexerClass = supported.get(formatIdentifier);
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
    public DocIndexer get(String formatIdentifier, DocWriter indexer, String documentName, File f, Charset cs) {
        if (!isSupported(formatIdentifier))
            throw new UnsupportedOperationException("Unknown format '" + formatIdentifier
                    + "', call isSupported(formatIdentifier) before attempting to get()");

        try {
            // Instantiate our DocIndexer class
            Class<? extends DocIndexer> docIndexerClass = supported.get(formatIdentifier);
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
    public DocIndexer get(String formatIdentifier, DocWriter indexer, String documentName, byte[] contents, Charset cs) {
        if (!isSupported(formatIdentifier))
            throw new UnsupportedOperationException("Unknown format '" + formatIdentifier
                    + "', call isSupported(formatIdentifier) before attempting to get()");

        try {
            // Instantiate our DocIndexer class
            Class<? extends DocIndexer> docIndexerClass = supported.get(formatIdentifier);
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
    public String formatError(String formatIdentifier) {
        return null;
    }
}

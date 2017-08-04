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

import nl.inl.blacklab.index.config.ConfigInputFormat;
import nl.inl.util.UnicodeStream;

public class DocIndexerFactoryClass implements DocIndexerFactory {
    private final Class<? extends DocIndexer> docIndexerClass;

    public DocIndexerFactoryClass(Class<? extends DocIndexer> docIndexerClass) {
        this.docIndexerClass = docIndexerClass;
    }

    @Override
    public DocIndexer get(Indexer indexer, String documentName, Reader reader) {
        try {
            // Instantiate our DocIndexer class
            Constructor<? extends DocIndexer> constructor;
            DocIndexer docIndexer;
            try {
                // NOTE: newer DocIndexers have a constructor that takes only an Indexer, not the document
                // being indexed. This allows us more flexibility in how we supply the document to this object
                // (e.g. as a file, a byte array, a reader, ...).
                // Assume this is a newer DocIndexer, and only construct it the old way if this fails.
                constructor = docIndexerClass.getConstructor(Indexer.class);
                docIndexer = constructor.newInstance();
                docIndexer.setIndexer(indexer);
                docIndexer.setDocumentName(documentName);
                docIndexer.setDocument(reader);
            } catch (NoSuchMethodException e) {
                // No, this is an older DocIndexer that takes document name and reader directly.
                constructor = docIndexerClass.getConstructor(Indexer.class, String.class, Reader.class);
                docIndexer = constructor.newInstance(indexer, documentName, reader);
            }
            return docIndexer;
        } catch (SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException |
                InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DocIndexer get(Indexer indexer, String documentName, InputStream is, Charset cs) {
        try {
            // Instantiate our DocIndexer class
            Constructor<? extends DocIndexer> constructor;
            DocIndexer docIndexer;
            try {
                constructor = docIndexerClass.getConstructor(Indexer.class);
                docIndexer = constructor.newInstance();
                docIndexer.setIndexer(indexer);
                docIndexer.setDocumentName(documentName);
                docIndexer.setDocument(is, cs);
            } catch (NoSuchMethodException e) {
                // No, this is an older DocIndexer that takes document name and reader directly.
                constructor = docIndexerClass.getConstructor(Indexer.class, String.class, Reader.class);
                docIndexer = constructor.newInstance(indexer, documentName, new InputStreamReader(is, cs));
            }
            return docIndexer;
        } catch (SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DocIndexer get(Indexer indexer, String documentName, File f, Charset cs) {
        try {
            // Instantiate our DocIndexer class
            Constructor<? extends DocIndexer> constructor;
            DocIndexer docIndexer;
            try {
                constructor = docIndexerClass.getConstructor(Indexer.class);
                docIndexer = constructor.newInstance();
                docIndexer.setIndexer(indexer);
                docIndexer.setDocumentName(documentName);
                docIndexer.setDocument(f, cs);
            } catch (NoSuchMethodException e) {
                // No, this is an older DocIndexer that takes document name and reader directly.
                constructor = docIndexerClass.getConstructor(Indexer.class, String.class, Reader.class);
                UnicodeStream is = new UnicodeStream(new FileInputStream(f), Indexer.DEFAULT_INPUT_ENCODING);
                docIndexer = constructor.newInstance(indexer, documentName, new InputStreamReader(is, cs));
            }
            return docIndexer;
        } catch (SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException | NoSuchMethodException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DocIndexer get(Indexer indexer, String documentName, byte[] contents, Charset cs) {
        try {
            // Instantiate our DocIndexer class
            Constructor<? extends DocIndexer> constructor;
            DocIndexer docIndexer;
            try {
                constructor = docIndexerClass.getConstructor(Indexer.class);
                docIndexer = constructor.newInstance();
                docIndexer.setIndexer(indexer);
                docIndexer.setDocumentName(documentName);
                docIndexer.setDocument(contents, cs);
            } catch (NoSuchMethodException e) {
                // No, this is an older DocIndexer that takes document name and reader directly.
                constructor = docIndexerClass.getConstructor(Indexer.class, String.class, Reader.class);
                docIndexer = constructor.newInstance(indexer, documentName, new InputStreamReader(new ByteArrayInputStream(contents), cs));
            }
            return docIndexer;
        } catch (SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException | NoSuchMethodException e) {
            // TODO Auto-generated catch block
            throw new RuntimeException(e);
        }
    }

    @Override
    public ConfigInputFormat getConfig() {
        return null;
    }
}
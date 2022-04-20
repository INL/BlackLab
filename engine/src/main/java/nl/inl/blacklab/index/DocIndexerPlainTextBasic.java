package nl.inl.blacklab.index;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Constructor;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.StoredField;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.MaxDocsReached;
import nl.inl.blacklab.index.annotated.AnnotatedFieldWriter;
import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.blacklab.index.annotated.AnnotationWriter.SensitivitySetting;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataWriter;

/**
 * Simple example indexer for plain text files. Reads a line, chops it into
 * words to index and keeps track of word positions.
 */
public class DocIndexerPlainTextBasic extends DocIndexerAbstract {

    /**
     * Annotated field where different aspects (word form, named entity status, etc.)
     * of the main content of the document are captured for indexing.
     */
    AnnotatedFieldWriter contentsField;

    /** The main annotation (usually "word") */
    AnnotationWriter annotMain;

    /** The punctuation annotation */
    AnnotationWriter annotPunct;

    /**
     * Our external metadata fetcher (if any), responsible for looking up the
     * metadata and adding it to the Lucene document.
     */
    MetadataFetcher metadataFetcher;

    @SuppressWarnings("deprecation")
    public DocIndexerPlainTextBasic(DocWriter indexer, String fileName, Reader reader) {
        super(indexer, fileName, reader);

        // Define the properties that make up our annotated field
        String mainPropName = AnnotatedFieldNameUtil.getDefaultMainAnnotationName();
        contentsField = new AnnotatedFieldWriter(Indexer.DEFAULT_CONTENTS_FIELD_NAME, mainPropName,
                getSensitivitySetting(mainPropName), false);
        annotMain = contentsField.mainAnnotation();
        String propName = AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME;
        annotPunct = contentsField.addAnnotation(null, propName, getSensitivitySetting(propName), false);
        IndexMetadataWriter indexMetadata = indexer.indexWriter().metadata();
        indexMetadata.registerAnnotatedField(contentsField);
    }

    /**
     * Get the external metadata fetcher for this indexer, if any.
     *
     * The metadata fetcher can be configured through the "metadataFetcherClass"
     * parameter.
     *
     * @return the metadata fetcher if any, or null if there is none.
     */
    MetadataFetcher getMetadataFetcher() {
        if (metadataFetcher == null) {
            @SuppressWarnings("deprecation")
            String metadataFetcherClassName = getParameter("metadataFetcherClass");
            if (metadataFetcherClassName != null) {
                try {
                    Class<? extends MetadataFetcher> metadataFetcherClass = Class.forName(metadataFetcherClassName)
                            .asSubclass(MetadataFetcher.class);
                    Constructor<? extends MetadataFetcher> ctor = metadataFetcherClass.getConstructor(DocIndexer.class);
                    metadataFetcher = ctor.newInstance(this);
                } catch (ReflectiveOperationException | IllegalArgumentException e) {
                    throw BlackLabRuntimeException.wrap(e);
                }
            }
        }
        return metadataFetcher;
    }

    public AnnotationWriter getAnnotPunct() {
        return annotPunct;
    }

    public AnnotationWriter getMainAnnotation() {
        return annotMain;
    }

    public AnnotatedFieldWriter getContentsField() {
        return contentsField;
    }

    /**
     * Returns the current word in the content.
     *
     * This is the position the next word will be stored at.
     *
     * @return the current word position
     */
    public int getWordPosition() {
        return annotMain.lastValuePosition() + 1;
    }

    public AnnotationWriter addAnnotation(String propName, SensitivitySetting sensitivity) {
        return contentsField.addAnnotation(null, propName, sensitivity);
    }

    @Override
    public void index() throws IOException, MalformedInputFile {
        BufferedReader r = new BufferedReader(reader);
        boolean firstWord = true;

        // Start a new Lucene document
        currentLuceneDoc = new Document();
        addMetadataField("fromInputFile", documentName);
        addMetadataFieldsFromParameters();
        getDocWriter().listener().documentStarted(documentName);

        while (true) {
            // For each line, split on whitespace and index each word.
            String line = r.readLine();
            if (line == null)
                break;
            String[] words = line.trim().split("\\s+");
            for (int i = 0; i < words.length; i++) {
                // Handle space and punctuation between words. Instead of always using a hardcoded
                // space,
                // you would want to use smarter tokenization and actually capture the space and
                // punctuation between words (right now the punctuation becomes part of the word,
                // because
                // we naively split on whitespace).
                String punctuation = "";
                if (!firstWord) {
                    punctuation = " ";
                    processContent(punctuation);
                }
                firstWord = false;
                annotPunct.addValue(punctuation);

                // Handle the word itself, including character positions.
                contentsField.addStartChar(getCharacterPosition());
                processContent(words[i]); // add word to content store
                contentsField.addEndChar(getCharacterPosition());
                annotMain.addValue(words[i]); // add word to index

                // Report progress regularly but not too often
                wordsDone++;
                if (wordsDone != 0 && wordsDone % 5000 == 0) {
                    reportCharsProcessed();
                    reportTokensProcessed();
                }
            }

            // Make sure all the properties have an equal number of values.
            // See what annotation has the highest position
            // (in practice, only starttags and endtags should be able to have
            // a position one higher than the rest)
            int lastValuePos = 0;
            for (AnnotationWriter prop : contentsField.annotationWriters()) {
                if (prop.lastValuePosition() > lastValuePos)
                    lastValuePos = prop.lastValuePosition();
            }

            // Make sure we always have one more token than the number of
            // words, so there's room for any tags after the last word, and we
            // know we should always skip the last token when matching.
            if (annotMain.lastValuePosition() == lastValuePos)
                lastValuePos++;

            // Add empty values to all lagging properties
            for (AnnotationWriter prop : contentsField.annotationWriters()) {
                while (prop.lastValuePosition() < lastValuePos) {
                    prop.addValue("");
                    if (prop.hasPayload())
                        prop.addPayload(null);
                    if (prop == annotMain) {
                        contentsField.addStartChar(getCharacterPosition());
                        contentsField.addEndChar(getCharacterPosition());
                    }
                }
            }

            // Finish storing the document in the document store (parts of it
            // may already have been written because we write in chunks to save memory),
            // retrieve the content id, and store that in Lucene.
            // (Note that we do this after adding the "extra closing token", so the character
            // positions for the closing token still make (some) sense)
            int contentId = storeCapturedContent();
            String contentIdFieldName = AnnotatedFieldNameUtil.contentIdField(contentsField.name());
            currentLuceneDoc.add(new IntPoint(contentIdFieldName, contentId));
            currentLuceneDoc.add(new StoredField(contentIdFieldName, contentId));

            // Store the different properties of the annotated contents field that
            // were gathered in lists while parsing.
            contentsField.addToLuceneDoc(currentLuceneDoc);

            // Add field with all its annotations to the forward index
            addToForwardIndex(contentsField);

            // If there's an external metadata fetcher, call it now so it can
            // add the metadata for this document and (optionally) store the
            // metadata document in the content store (and the corresponding id in the
            // Lucene doc)
            MetadataFetcher m = getMetadataFetcher();
            if (m != null) {
                m.addMetadata();
            }

            addMetadataToDocument();

            try {
                // Add Lucene doc to indexer
                getDocWriter().add(currentLuceneDoc);
            } catch (Exception e) {
                throw BlackLabRuntimeException.wrap(e);
            }

            // Report progress
            reportCharsProcessed();
            reportTokensProcessed();

            documentDone(documentName);

            // Reset contents field for next document
            contentsField.clear(true);
            currentLuceneDoc = null;

            // Stop if required
            if (!getDocWriter().continueIndexing())
                throw new MaxDocsReached();

        }

        if (nDocumentsSkipped > 0)
            System.err.println("Skipped " + nDocumentsSkipped + " large documents");
    }

}

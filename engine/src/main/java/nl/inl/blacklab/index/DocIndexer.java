/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package nl.inl.blacklab.index;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.util.BytesRef;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.index.annotated.AnnotatedFieldWriter;
import nl.inl.blacklab.index.annotated.AnnotationWriter.SensitivitySetting;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.FieldType;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataImpl;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataWriter;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.MetadataFieldImpl;
import nl.inl.blacklab.search.indexmetadata.UnknownCondition;
import nl.inl.util.UnicodeStream;

/**
 * Indexes a file.
 */
public abstract class DocIndexer implements AutoCloseable {

    protected static final Logger logger = LogManager.getLogger(DocIndexer.class);

    public static final int MAX_DOCVALUES_LENGTH = Short.MAX_VALUE - 100; // really - 1, but let's be extra safe

    protected DocWriter docWriter;

    /** Do we want to omit norms? (Default: yes) */
    protected boolean omitNorms = true;

    /**
     * File we're currently parsing. This can be useful for storing the original
     * filename in the index.
     */
    protected String documentName;

    /**
     * The Lucene Document we're currently constructing (corresponds to the document
     * we're indexing)
     */
    protected Document currentLuceneDoc;

    /**
     * Document metadata. Added at the end to deal with unknown values, multiple occurrences
     * (only the first is actually indexed, because of DocValues, among others), etc.
     */
    protected Map<String, List<String>> metadataFieldValues = new HashMap<>();

    /**
     * Parameters passed to this indexer
     */
    protected Map<String, String> parameters = new HashMap<>();

    Set<String> numericFields = new HashSet<>();

    @Override
    public abstract void close();

    public Document getCurrentLuceneDoc() {
        return currentLuceneDoc;
    }

    /**
     * Returns our DocWriter object
     *
     * @return the DocWriter object
     */
    public DocWriter getDocWriter() {
        return docWriter;
    }

    /**
     * Set the DocWriter object.
     *
     * We use this to add documents to the index.
     *
     * Called by Indexer when the DocIndexer is instantiated.
     *
     * @param docWriter our DocWriter object
     */
    public void setDocWriter(DocWriter docWriter) {
        this.docWriter = docWriter;

        if (docWriter != null) {
            // Get our parameters from the indexer
            Map<String, String> indexerParameters = docWriter.indexerParameters();
            if (indexerParameters != null)
                setParameters(indexerParameters);
        }
    }

    /**
     * Set the file name of the document to index.
     *
     * @param documentName name of the document
     */
    public void setDocumentName(String documentName) {
        this.documentName = documentName == null ? "?" : documentName;
    }

    /**
     * @deprecated use {@link #setDocument(byte[], Charset)}
     * Set the document to index.
     *
     * NOTE: you should generally prefer calling the File or byte[] versions of this
     * method, as those can be more efficient (e.g. when using DocIndexer that
     * parses using VTD-XML).
     *
     * @param reader document
     */
    @Deprecated
    public abstract void setDocument(Reader reader);

    /**
     * Set the document to index.
     *
     * @param is document contents
     * @param cs charset to use if no BOM found, or null for the default (utf-8)
     */
    public void setDocument(InputStream is, Charset cs) {
        try {
            UnicodeStream unicodeStream = new UnicodeStream(is, cs);
            Charset detectedCharset = unicodeStream.getEncoding();
            setDocument(new InputStreamReader(unicodeStream, detectedCharset));
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    /**
     *
     * Set the document to index.
     *
     * @param contents document contents
     * @param cs charset to use if no BOM found, or null for the default (utf-8)
     */
    public void setDocument(byte[] contents, Charset cs) {
        setDocument(new ByteArrayInputStream(contents), cs);
    }

    /**
     * Set the document to index.
     *
     * @param file file to index
     * @param charset charset to use if no BOM found, or null for the default
     *            (utf-8)
     * @throws FileNotFoundException if not found
     */
    public void setDocument(File file, Charset charset) throws FileNotFoundException {
        setDocument(new FileInputStream(file), charset);
    }

    /**
     * Index documents contained in a file.
     *
     * @throws MalformedInputFile if the input file wasn't valid
     * @throws IOException if an I/O error occurred
     * @throws PluginException if an error occurred in a plugin
     */
    public abstract void index() throws IOException, MalformedInputFile, PluginException;

    /**
     * Check if the specified parameter has a value
     *
     * @param name parameter name
     * @return true iff the parameter has a value
     * @deprecated use a DocIndexerConfig-based indexer
     */
    @Deprecated
    public boolean hasParameter(String name) {
        return parameters.containsKey(name);
    }

    /**
     * Set a parameter for this indexer (such as which type of metadata block to
     * process)
     *
     * @param name parameter name
     * @param value parameter value
     * @deprecated use a DocIndexerConfig-based indexer
     */
    @Deprecated
    public void setParameter(String name, String value) {
        parameters.put(name, value);
    }

    /**
     * Set a number of parameters for this indexer
     *
     * @param param the parameter names and values
     * @deprecated use a DocIndexerConfig-based indexer
     */
    @Deprecated
    public void setParameters(Map<String, String> param) {
        for (Map.Entry<String, String> e : param.entrySet()) {
            parameters.put(e.getKey(), e.getValue());
        }
    }

    /**
     * Get a parameter that was set for this indexer
     *
     * @param name parameter name
     * @param defaultValue parameter default value
     * @return the parameter value (or the default value if it was not specified)
     * @deprecated use ConfigInputFormat, IndexMetadata
     */
    @Deprecated
    public String getParameter(String name, String defaultValue) {
        String value = parameters.get(name);
        if (value == null)
            return defaultValue;
        return value;
    }

    /**
     * Get a parameter that was set for this indexer
     *
     * @param name parameter name
     * @return the parameter value (or null if it was not specified)
     * @deprecated use a DocIndexerConfig-based indexer
     */
    @Deprecated
    public String getParameter(String name) {
        return getParameter(name, null);
    }

    /**
     * Get a parameter that was set for this indexer
     *
     * @param name parameter name
     * @param defaultValue parameter default value
     * @return the parameter value (or the default value if it was not specified)
     * @deprecated use a DocIndexerConfig-based indexer
     */
    @Deprecated
    public boolean getParameter(String name, boolean defaultValue) {
        String value = parameters.get(name);
        if (value == null)
            return defaultValue;
        value = value.trim().toLowerCase();
        return value.equals("true") || value.equals("1") || value.equals("yes");
    }

    /**
     * Get a parameter that was set for this indexer
     *
     * @param name parameter name
     * @param defaultValue parameter default value
     * @return the parameter value (or the default value if it was not specified)
     * @deprecated use a DocIndexerConfig-based indexer
     */
    @Deprecated
    public int getParameter(String name, int defaultValue) {
        String value = parameters.get(name);
        if (value == null)
            return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    protected boolean tokenizeField(String name) {
        // (Also check the old (Lucene 3.x) term, "analyzed")
        String parName = hasParameter(name + "_tokenized") ? name + "_tokenized" : name + "_analyzed";
        return getParameter(parName, true);
    }

    /**
     * Return the fieldtype to use for the specified field.
     *
     * @param fieldName the field name
     * @return the fieldtype
     * @deprecated use a DocIndexerConfig-based indexer
     */
    @Deprecated
    public FieldType getMetadataFieldTypeFromIndexerProperties(String fieldName) {
        // (Also check the old (Lucene 3.x) term, "analyzed")
        String parName = hasParameter(fieldName + "_tokenized") ? fieldName + "_tokenized" : fieldName + "_analyzed";
        if (getParameter(parName, true))
            return FieldType.TOKENIZED;
        return FieldType.UNTOKENIZED;
    }

    protected org.apache.lucene.document.FieldType luceneTypeFromIndexMetadataType(FieldType type) {
        switch (type) {
        case NUMERIC:
            throw new IllegalArgumentException("Numeric types should be indexed using IntField, etc.");
        case TOKENIZED:
            return docWriter.metadataFieldType(true);
        case UNTOKENIZED:
            return docWriter.metadataFieldType(false);
        default:
            throw new IllegalArgumentException("Unknown field type: " + type);
        }
    }

    /**
     * Enables or disables norms. Norms are disabled by default.
     *
     * The method name was chosen to match Lucene's Field.setOmitNorms(). Norms are
     * only required if you want to use document-length-normalized scoring.
     *
     * @param b if true, doesn't store norms; if false, does store norms
     */
    public void setOmitNorms(boolean b) {
        omitNorms = b;
    }

    public void addNumericFields(Collection<String> fields) {
        numericFields.addAll(fields);
    }

    boolean continueIndexing() {
        return docWriter.continueIndexing();
    }

    protected void warn(String msg) {
        docWriter.listener().warning(msg);
    }

    public List<String> getMetadataField(String name) {
        return metadataFieldValues.get(name);
    }

    public void addMetadataField(String name, String value) {
        if (!AnnotatedFieldNameUtil.isValidXmlElementName(name))
            logger.warn("Field name '" + name
                    + "' is discouraged (field/annotation names should be valid XML element names)");

        if (name == null || value == null) {
            warn("Incomplete metadata field: " + name + "=" + value + " (skipping)");
            return;
        }

        value = value.trim();
        if (!value.isEmpty()) {
            metadataFieldValues.computeIfAbsent(name, __ -> new ArrayList<>()).add(value);
            IndexMetadataWriter indexMetadata = docWriter.indexWriter().metadata();
            indexMetadata.registerMetadataField(name);
        }
    }

    /**
     * Translate a field name before adding it to the Lucene document.
     *
     * By default, simply returns the input. May be overridden to change the name of
     * a metadata field as it is indexed.
     *
     * @param from original metadata field name
     * @return new name
     */
    protected String optTranslateFieldName(String from) {
        return from;
    }

    /**
     * When all metadata values have been set, call this to add the to the Lucene document.
     *
     * We do it this way because we don't want to add multiple values for a field (DocValues and
     * Document.get() only deal with the first value added), and we want to set an "unknown value"
     * in certain conditions, depending on the configuration.
     */
    public void addMetadataToDocument() {
        // See what metadatafields are missing or empty and add unknown value if desired.
        IndexMetadataImpl indexMetadata = (IndexMetadataImpl)docWriter.indexWriter().metadata();
        Map<String, String> unknownValuesToUse = new HashMap<>();
        List<String> fields = indexMetadata.metadataFields().names();
        for (int i = 0; i < fields.size(); i++) {
            MetadataField fd = indexMetadata.metadataField(fields.get(i));
            if (fd.type() == FieldType.NUMERIC)
                continue;
            boolean missing = false, empty = false;
            List<String> currentValue = getMetadataField(fd.name());
            if (currentValue == null)
                missing = true;
            else if (currentValue.isEmpty() || currentValue.stream().allMatch(String::isEmpty))
                empty = true;
            UnknownCondition cond = fd.unknownCondition();
            boolean useUnknownValue = false;
            switch (cond) {
            case EMPTY:
                useUnknownValue = empty;
                break;
            case MISSING:
                useUnknownValue = missing;
                break;
            case MISSING_OR_EMPTY:
                useUnknownValue = missing || empty;
                break;
            case NEVER:
                useUnknownValue = false;
                break;
            }
            if (useUnknownValue) {
                if (empty) {
                    // Don't count this as a value, count the unknown value
                    for (String value : currentValue) {
                        ((MetadataFieldImpl)indexMetadata.metadataFields().get(fd.name())).removeValue(value);
                    }
                }
                unknownValuesToUse.put(optTranslateFieldName(fd.name()), fd.unknownValue());
            }
        }
        for (Entry<String, String> e: unknownValuesToUse.entrySet()) {
            metadataFieldValues.put(e.getKey(), Arrays.asList(e.getValue()));
        }
        for (Entry<String, List<String>> e: metadataFieldValues.entrySet()) {
            addMetadataFieldToDocument(e.getKey(), e.getValue());
        }
        metadataFieldValues.clear();
    }

    private void addMetadataFieldToDocument(String name, List<String> values) {
        IndexMetadataWriter indexMetadata = docWriter.indexWriter().metadata();
        //indexMetadata.registerMetadataField(name);

        MetadataFieldImpl desc = (MetadataFieldImpl)indexMetadata.metadataFields().get(name);
        FieldType type = desc.type();
        for (String value : values) {
            desc.addValue(value);
        }


        // There used to be another way of specifying metadata field type,
        // via indexer.properties. This is still supported, but deprecated.
        FieldType shouldBeType = getMetadataFieldTypeFromIndexerProperties(name);
        if (type == FieldType.TOKENIZED
                && shouldBeType != FieldType.TOKENIZED) {
            // indexer.properties overriding default type
            type = shouldBeType;
        }

        if (type != FieldType.NUMERIC) {
            for (String value : values) {
                currentLuceneDoc.add(new Field(name, value, luceneTypeFromIndexMetadataType(type)));
                // If a value is too long (more than 32K), just truncate it a bit.
                // This should be very rare and would generally only affect sorting/grouping, if anything.
                if (value.length() > MAX_DOCVALUES_LENGTH / 6) { // only when it might be too large...
                    // While it's really too large
                    byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
                    while (utf8.length > MAX_DOCVALUES_LENGTH) {
                        // assume all characters take two bytes, truncate and try again
                        int overshoot = utf8.length - MAX_DOCVALUES_LENGTH;
                        int truncateAt = value.length() - 2 * overshoot;
                        if (truncateAt < 1)
                            truncateAt = 1;
                        value = value.substring(0, truncateAt);
                        utf8 = value.getBytes(StandardCharsets.UTF_8);
                    }
                }
                currentLuceneDoc.add(new SortedSetDocValuesField(name, new BytesRef(value))); // docvalues for efficient sorting/grouping
            }
        }
        if (type == FieldType.NUMERIC || numericFields.contains(name)) {
            String numFieldName = name;
            if (type != FieldType.NUMERIC) {
                numFieldName += "Numeric";
            }

            boolean firstValue = true;
            for (String value : values) {
                // Index these fields as numeric too, for faster range queries
                // (we do both because fields sometimes aren't exclusively numeric)
                int n;
                try {
                    n = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    // This just happens sometimes, e.g. given multiple years, or
                    // descriptive text like "around 1900". OK to ignore.
                    n = 0;
                }
                IntField nf = new IntField(numFieldName, n, Store.YES);
                currentLuceneDoc.add(nf);
                if (firstValue)
                    currentLuceneDoc.add(new NumericDocValuesField(numFieldName, n)); // docvalues for efficient sorting/grouping
                else {
                    warn(documentName + " contains multiple values for single-valued numeric field " + numFieldName + "(values: " + StringUtils.join(values, "; ") + ")");
                }
                firstValue = false;
            }
        }
    }

    /**
     * If any metadata fields were supplied in the indexer parameters, add them now.
     *
     * NOTE: we always add these untokenized (because they're usually just
     * indications of which data set a set of files belongs to), but that means they
     * don't get lowercased or de-accented. Because metadata queries are always
     * desensitized, you can't use uppercase or accented letters in these values or
     * they will never be found. This should be addressed.
     *
     * NOTE2: This way of adding metadata values is deprecated. Use an input format config
     * file instead, and configure a field with a fixed value.
     */
    protected void addMetadataFieldsFromParameters() {
        for (Entry<String, String> e : parameters.entrySet()) {
            if (e.getKey().startsWith("meta-")) {
                String fieldName = e.getKey().substring(5);
                String fieldValue = e.getValue();
                currentLuceneDoc.add(new Field(fieldName, fieldValue, docWriter.metadataFieldType(false)));
            }
        }
    }

    @Deprecated
    public SensitivitySetting getSensitivitySetting(String annotationName) {
        // See if it's specified in a parameter
        String strSensitivity = getParameter(annotationName + "_sensitivity");
        if (strSensitivity != null) {
            if (strSensitivity.equals("i"))
                return SensitivitySetting.ONLY_INSENSITIVE;
            if (strSensitivity.equals("s"))
                return SensitivitySetting.ONLY_SENSITIVE;
            if (strSensitivity.equals("si") || strSensitivity.equals("is"))
                return SensitivitySetting.SENSITIVE_AND_INSENSITIVE;
            if (strSensitivity.equals("all"))
                return SensitivitySetting.CASE_AND_DIACRITICS_SEPARATE;
        }

        // Not in parameter (or unrecognized value), use default based on
        // annotationName
        if (annotationName.equals(AnnotatedFieldNameUtil.getDefaultMainAnnotationName())
                || annotationName.equals(AnnotatedFieldNameUtil.LEMMA_ANNOT_NAME)) {
            // Word: default to sensitive/insensitive
            return SensitivitySetting.SENSITIVE_AND_INSENSITIVE;
        }
        if (annotationName.equals(AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME)) {
            // Punctuation: default to only insensitive
            return SensitivitySetting.ONLY_INSENSITIVE;
        }
        if (annotationName.equals(AnnotatedFieldNameUtil.TAGS_ANNOT_NAME)) {
            // XML tag properties: default to only sensitive
            return SensitivitySetting.ONLY_SENSITIVE;
        }

        // Unrecognized; default to only insensitive
        return SensitivitySetting.ONLY_INSENSITIVE;
    }

    /**
     * Add the field, with all its properties, to the forward index.
     *
     * @param field field to add to the forward index
     */
    protected void addToForwardIndex(AnnotatedFieldWriter field) {
        docWriter.addToForwardIndex(field, currentLuceneDoc);
    }

    protected abstract int getCharacterPosition();

    /**
     * Report the amount of new characters processed since the last call
     */
    public abstract void reportCharsProcessed();

    /**
     * Report the amounf of new tokens processed since the last call
     */
    public abstract void reportTokensProcessed();

}

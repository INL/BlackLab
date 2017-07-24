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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.IntField;

import nl.inl.blacklab.index.complex.ComplexFieldProperty.SensitivitySetting;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.indexstructure.FieldType;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
import nl.inl.blacklab.search.indexstructure.MetadataFieldDesc;
import nl.inl.util.UnicodeStream;

/**
 * Indexes a file.
 */
public abstract class DocIndexer {

    protected Indexer indexer;

    /** Do we want to omit norms? (Default: yes) */
    protected boolean omitNorms = true;

    /**
     * File we're currently parsing. This can be useful for storing the original filename in the
     * index.
     */
    protected String documentName;

    /**
     * The Lucene Document we're currently constructing (corresponds to the
     * document we're indexing)
     */
    protected Document currentLuceneDoc;

    /** Number of words processed (for reporting progress) */
    protected int wordsDone;

    /**
     * Parameters passed to this indexer
     */
    protected Map<String, String> parameters = new HashMap<>();

    Set<String> numericFields = new HashSet<>();

    public Document getCurrentLuceneDoc() {
        return currentLuceneDoc;
    }

	/**
	 * Thrown when the maximum number of documents has been reached
	 */
	public static class MaxDocsReachedException extends RuntimeException {
		//
	}

    /**
     * Returns our Indexer object
     * @return the Indexer object
     */
    public Indexer getIndexer() {
        return indexer;
    }

    /**
     * Set the indexer object. Called by Indexer when the DocIndexer is instantiated.
     *
     * @param indexer our indexer object
     */
    public void setIndexer(Indexer indexer) {
        this.indexer = indexer;

        if (indexer != null) {
            // Get our parameters from the indexer
            Map<String, String> indexerParameters = indexer.getIndexerParameters();
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
     * Set the document to index.
     *
     * NOTE: you should generally prefer calling the File or byte[] versions
     * of this method, as those can be more efficient (e.g. when using DocIndexer that
     * parses using VTD-XML).
     *
     * @param reader document
     */
	public abstract void setDocument(Reader reader);

    /**
     * Set the document to index.
     *
     * @param is document contents
     * @param cs charset to use if no BOM found, or null for the default (utf-8)
     */
    public void setDocument(InputStream is, Charset cs) {
        try {
            setDocument(new InputStreamReader(new UnicodeStream(is, cs)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
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
     * @param charset charset to use if no BOM found, or null for the default (utf-8)
     * @throws FileNotFoundException if not found
     */
    public void setDocument(File file, Charset charset) throws FileNotFoundException {
        setDocument(new FileInputStream(file), charset);
    }

    /**
	 * Index documents contained in a file.
	 *
	 * @throws Exception
	 */
	public abstract void index() throws Exception;

    /**
     * Check if the specified parameter has a value
     * @param name parameter name
     * @return true iff the parameter has a value
     * @deprecated use ConfigInputFormat, IndexStructure
     */
    @Deprecated
    public boolean hasParameter(String name) {
        return parameters.containsKey(name);
    }

    /**
     * Set a parameter for this indexer (such as which type of metadata block to process)
     * @param name parameter name
     * @param value parameter value
     * @deprecated use ConfigInputFormat, IndexStructure
     */
    @Deprecated
    public void setParameter(String name, String value) {
        parameters.put(name, value);
    }

    /**
     * Set a number of parameters for this indexer
     * @param param the parameter names and values
     * @deprecated use ConfigInputFormat, IndexStructure
     */
    @Deprecated
    public void setParameters(Map<String, String> param) {
        for (Map.Entry<String, String> e: param.entrySet()) {
            parameters.put(e.getKey(), e.getValue());
        }
    }

    /**
     * Get a parameter that was set for this indexer
     * @param name parameter name
     * @param defaultValue parameter default value
     * @return the parameter value (or the default value if it was not specified)
     * @deprecated use ConfigInputFormat, IndexStructure
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
     * @param name parameter name
     * @return the parameter value (or null if it was not specified)
     * @deprecated use ConfigInputFormat, IndexStructure
     */
    @Deprecated
    public String getParameter(String name) {
        return getParameter(name, null);
    }

    /**
     * Get a parameter that was set for this indexer
     * @param name parameter name
     * @param defaultValue parameter default value
     * @return the parameter value (or the default value if it was not specified)
     * @deprecated use ConfigInputFormat, IndexStructure
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
     * @param name parameter name
     * @param defaultValue parameter default value
     * @return the parameter value (or the default value if it was not specified)
     * @deprecated use ConfigInputFormat, IndexStructure
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
     * @param fieldName the field name
     * @return the fieldtype
     * @deprecated use ConfigInputFormat, IndexStructure
     */
    @Deprecated
    public FieldType getMetadataFieldTypeFromIndexerProperties(String fieldName) {
        // (Also check the old (Lucene 3.x) term, "analyzed")
        String parName = hasParameter(fieldName + "_tokenized") ? fieldName + "_tokenized" : fieldName + "_analyzed";
        if (getParameter(parName, true))
            return FieldType.TOKENIZED;
        return FieldType.UNTOKENIZED;
    }

    protected org.apache.lucene.document.FieldType luceneTypeFromIndexStructType(FieldType type) {
        switch (type) {
        case NUMERIC:
            throw new IllegalArgumentException("Numeric types should be indexed using IntField, etc.");
        case TOKENIZED:
            return indexer.getMetadataFieldType(true);
        case UNTOKENIZED:
            return indexer.getMetadataFieldType(false);
        default:
            throw new IllegalArgumentException("Unknown field type: " + type);
        }
    }

    public void reportTokensProcessed(int n) {
        indexer.getListener().tokensDone(n);
    }

    /**
     * Enables or disables norms. Norms are disabled by default.
     *
     * The method name was chosen to match Lucene's Field.setOmitNorms().
     * Norms are only required if you want to use document-length-normalized scoring.
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
        return indexer.continueIndexing();
    }

    public void addMetadataField(String name, String value) {

        IndexStructure struct = indexer.getSearcher().getIndexStructure();
        struct.registerMetadataField(name);

        MetadataFieldDesc desc = struct.getMetadataFieldDesc(name);
        FieldType type = desc.getType();
        desc.addValue(value);

        // There used to be another way of specifying metadata field type,
        // via indexer.properties. This is still supported, but deprecated.
        FieldType shouldBeType = getMetadataFieldTypeFromIndexerProperties(name);
        if (type == FieldType.TOKENIZED
                && shouldBeType != FieldType.TOKENIZED) {
            // indexer.properties overriding default type
            type = shouldBeType;
        }

        if (type != FieldType.NUMERIC) {
            currentLuceneDoc.add(new Field(name, value, luceneTypeFromIndexStructType(type)));
        }
        if (type == FieldType.NUMERIC || numericFields.contains(name)) {
            String numFieldName = name;
            if (type != FieldType.NUMERIC) {
                numFieldName += "Numeric";
            }
            // Index these fields as numeric too, for faster range queries
            // (we do both because fields sometimes aren't exclusively numeric)
            int n = 0;
            try {
                n = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // This just happens sometimes, e.g. given multiple years, or
                // descriptive text like "around 1900". OK to ignore.
            }
            IntField nf = new IntField(numFieldName, n, Store.YES);
            currentLuceneDoc.add(nf);
        }
    }

    /**
     * If any metadata fields were supplied in the indexer parameters,
     * add them now.
     *
     * NOTE: we always add these untokenized (because they're usually just
     * indications of which data set a set of files belongs to), but that
     * means they don't get lowercased or de-accented. Because metadata queries
     * are always desensitized, you can't use uppercase or accented letters in
     * these values or they will never be found. This should be addressed.
     */
    protected void addMetadataFieldsFromParameters() {
        for (Entry<String, String> e: parameters.entrySet()) {
            if (e.getKey().startsWith("meta-")) {
                String fieldName = e.getKey().substring(5);
                String fieldValue = e.getValue();
                currentLuceneDoc.add(new Field(fieldName, fieldValue, indexer.getMetadataFieldType(false)));
            }
        }
    }

    @Deprecated
    protected SensitivitySetting getSensitivitySetting(String propName) {
        // See if it's specified in a parameter
        String strSensitivity = getParameter(propName + "_sensitivity");
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
        // propName
        if (propName.equals(ComplexFieldUtil.getDefaultMainPropName())
                || propName.equals(ComplexFieldUtil.LEMMA_PROP_NAME)) {
            // Word: default to sensitive/insensitive
            return SensitivitySetting.SENSITIVE_AND_INSENSITIVE;
        }
        if (propName.equals(ComplexFieldUtil.PUNCTUATION_PROP_NAME)) {
            // Punctuation: default to only insensitive
            return SensitivitySetting.ONLY_INSENSITIVE;
        }
        if (propName.equals(ComplexFieldUtil.START_TAG_PROP_NAME)
                || propName.equals(ComplexFieldUtil.END_TAG_PROP_NAME)) {
            // XML tag properties: default to only sensitive
            return SensitivitySetting.ONLY_SENSITIVE;
        }

        // Unrecognized; default to only insensitive
        return SensitivitySetting.ONLY_INSENSITIVE;
    }

	public abstract int getCharacterPosition();

}

package nl.inl.blacklab.index;

import org.apache.lucene.analysis.TokenStream;

/**
 * Generic interface for a BlackLab document being indexed.
 *
 * Either implemented using Lucene's Document class directly,
 * or through Solr's SolrInputDocument intermediary (which adds
 * schema validation, copyfields, etc.).
 */
public interface BLInputDocument {

    int MAX_DOCVALUES_LENGTH = Short.MAX_VALUE - 100; // really - 1, but let's be extra safe

    void addField(String name, String value, BLFieldType fieldType);

    void addStoredField(String name, String value);

    void addAnnotationField(String name, TokenStream tokenStream, BLFieldType fieldType);

    void addStoredNumericField(String name, int value, boolean addDocValue);

    void addTextualMetadataField(String name, String value, BLFieldType type);

    String get(String name);

    BLIndexObjectFactory indexObjectFactory();

}

package nl.inl.blacklab.index;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.util.BytesRef;

/**
 * Generic interface for a BlackLab document being indexed.
 *
 * Either implemented using Lucene's Document class directly,
 * or through Solr's SolrInputDocument intermediary (which adds
 * schema validation, copyfields, etc.).
 */
public interface BLInputDocument {

    void addField(String name, String value, BLFieldType fieldType);

    void addField(String name, TokenStream tokenStream, BLFieldType fieldType);

    void addSortedSetDocValuesField(String name, BytesRef value);

    void addIntPointField(String name, int n);

    void addStoredField(String name, String value);

    void addStoredField(String name, int n);

    void addNumericDocValuesField(String name, int n);

    String get(String name);

}

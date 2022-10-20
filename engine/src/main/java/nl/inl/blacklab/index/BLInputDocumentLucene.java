package nl.inl.blacklab.index;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.IndexableFieldType;
import org.apache.lucene.util.BytesRef;

public class BLInputDocumentLucene implements BLInputDocument {

    private final Document document;

    public BLInputDocumentLucene() {
        document = new Document();
    }

    IndexableFieldType luceneType(BLFieldType type) {
        return ((BLFieldTypeLucene)type).getLuceneFieldType();
    }

    public void addField(String name, String value, BLFieldType fieldType) {
        document.add(new Field(name, value, luceneType(fieldType)));
    }

    public void addField(String name, TokenStream tokenStream, BLFieldType fieldType) {
        document.add(new Field(name, tokenStream, luceneType(fieldType)));
    }

    public void addSortedSetDocValuesField(String name, BytesRef value) {
        document.add(new SortedSetDocValuesField(name, value));
    }

    public void addIntPointField(String name, int n) {
        document.add(new IntPoint(name, n));
    }

    public void addStoredField(String name, int n) {
        document.add(new StoredField(name, n));
    }

    public void addStoredField(String name, String value) {
        document.add(new StoredField(name, value));
    }

    public void addNumericDocValuesField(String name, int n) {
        document.add(new NumericDocValuesField(name, n));
    }

    public Document getDocument() {
        return document;
    }

    @Override
    public String get(String name) {
        return document.get(name);
    }
}

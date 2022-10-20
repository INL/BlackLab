package nl.inl.blacklab.index;

import org.apache.lucene.index.IndexableFieldType;

public class BLFieldTypeLucene implements BLFieldType {

    private final IndexableFieldType type;

    public BLFieldTypeLucene(IndexableFieldType type) {
        this.type = type;
    }

    public IndexableFieldType getLuceneFieldType() {
        return type;
    }
}

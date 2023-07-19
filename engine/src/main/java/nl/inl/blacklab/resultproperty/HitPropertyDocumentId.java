package nl.inl.blacklab.resultproperty;

import nl.inl.blacklab.search.results.Hits;

/**
 * A hit property for grouping per document id.
 * 
 * NOTE: prefer using HitPropertyDoc, which includes the actual 
 * Doc instance. 
 */
public class HitPropertyDocumentId extends HitProperty {

    static HitPropertyDocumentId deserializeProp() {
        return new HitPropertyDocumentId();
    }

    HitPropertyDocumentId(HitPropertyDocumentId prop, Hits hits, boolean invert) {
        super(prop, hits, invert);
    }

    public HitPropertyDocumentId() {
        super();
    }

    @Override
    public HitProperty copyWith(Hits newHits, boolean invert) {
        return new HitPropertyDocumentId(this, newHits, invert);
    }

    @Override
    public PropertyValueInt get(long hitIndex) {
        return new PropertyValueInt(hits.doc(hitIndex));
    }

    @Override
    public String name() {
        return "document: id";
    }

    @Override
    public int compare(long indexA, long indexB) {
        final int docA = hits.doc(indexA);
        final int docB = hits.doc(indexB);
        return reverse ? docB - docA : docA - docB;
    }

    @Override
    public String serialize() {
        return serializeReverse() + "docid";
    }

    @Override
    public DocProperty docPropsOnly() {
        DocPropertyId result = new DocPropertyId();
        return reverse ? result.reverse() : result;
    }

    @Override
    public PropertyValue docPropValues(PropertyValue value) {
        return value;
    }
    
    @Override
    public boolean isDocPropOrHitText() {
        return true;
    }
}

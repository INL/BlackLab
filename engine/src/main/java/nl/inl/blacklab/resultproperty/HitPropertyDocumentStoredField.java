package nl.inl.blacklab.resultproperty;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.util.PropertySerializeUtil;

/**
 * A hit property for grouping on a stored field in the corresponding Lucene
 * document.
 */
public class HitPropertyDocumentStoredField extends HitProperty {

    public static final String ID = DocPropertyStoredField.ID;

    final String fieldName;

    final private DocPropertyStoredField docPropStoredField;

    HitPropertyDocumentStoredField(HitPropertyDocumentStoredField prop, Hits hits, boolean invert) {
        super(prop, hits, invert);
        this.fieldName = prop.fieldName;
        this.docPropStoredField = prop.docPropStoredField;
    }

    public HitPropertyDocumentStoredField(BlackLabIndex index, String fieldName, String friendlyName) {
        super();
        this.fieldName = fieldName;
        this.docPropStoredField = new DocPropertyStoredField(index, fieldName, friendlyName);
    }

    public HitPropertyDocumentStoredField(BlackLabIndex index, String fieldName) {
        this(index, fieldName, fieldName);
    }

    @Override
    public HitProperty copyWith(Hits newHits, boolean invert) {
        return new HitPropertyDocumentStoredField(this, newHits, invert);
    }

    @Override
    public PropertyValueString get(long result) {
        return DocPropertyStoredField.fromArray(docPropStoredField.get(hits.doc(result)));
    }

    @Override
    public int compare(long a, long b) {
        final int docA = hits.doc(a);
        final int docB = hits.doc(b);
        int result = docPropStoredField.compare(docA, docB);
        return reverse ? -result : result;
    }

    @Override
    public String name() {
        return "document: " + docPropStoredField.name();
    }

    @Override
    public String serialize() {
        return serializeReverse() + PropertySerializeUtil.combineParts(ID, fieldName);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((fieldName == null) ? 0 : fieldName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        HitPropertyDocumentStoredField other = (HitPropertyDocumentStoredField) obj;
        if (fieldName == null) {
            if (other.fieldName != null)
                return false;
        } else if (!fieldName.equals(other.fieldName))
            return false;
        return true;
    }

    @Override
    public DocProperty docPropsOnly() {
        return reverse ? docPropStoredField.reverse() : docPropStoredField;
    }

    @Override
    public PropertyValue docPropValues(PropertyValue value) {
        return value;
    }
    
    @Override
    public boolean isDocPropOrHitText() {
        return true;
    }
    
    public String fieldName() {
        return fieldName;
    }
}

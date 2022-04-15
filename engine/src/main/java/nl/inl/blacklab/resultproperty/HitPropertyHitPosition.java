package nl.inl.blacklab.resultproperty;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Contexts;
import nl.inl.blacklab.search.results.Hits;

/**
 * A hit property for sorting on hit token position. Usually to be combined with
 * sorting on document id, for a fast and reproducible sort.
 */
public class HitPropertyHitPosition extends HitProperty {

    static HitPropertyHitPosition deserializeProp() {
        return new HitPropertyHitPosition();
    }

    HitPropertyHitPosition(HitPropertyHitPosition prop, Hits hits, boolean invert) {
        super(prop, hits, null, invert);
    }
    
    public HitPropertyHitPosition() {
        super();
    }

    @Override
    public HitProperty copyWith(Hits newHits, Contexts contexts, boolean invert) {
        return new HitPropertyHitPosition(this, newHits, invert);
    }

    @Override
    public PropertyValueInt get(long hitIndex) {
        return new PropertyValueInt(hits.start(hitIndex));
    }

    @Override
    public String name() {
        return "hit: position";
    }

    @Override
    public int compare(long indexA, long indexB) {
        int startA = hits.start(indexA);
        int startB = hits.start(indexB);
        int endA = hits.end(indexA);
        int endB = hits.end(indexB);
        
        if (startA == startB)
            return reverse ? endB - endA : endA - endB;
        return reverse ? startB - startA : startA - startB;
    }

    @Override
    public String serialize() {
        return serializeReverse() + "hitposition";
    }
    
    @Override
    public boolean isDocPropOrHitText() {
        return false;
    }
    
    @Override
    public ContextSize needsContextSize(BlackLabIndex index) {
        return null;
    }
}

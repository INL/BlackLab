package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * Methods for creating Kwics from hits.
 */
public class Kwics {
    
    /** Our hits object */
    private final Hits hits;

    /**
     * The KWIC data, if it has been retrieved.
     *
     * NOTE: this will always be null if not all the hits have been retrieved.
     */
    Map<Hit, Kwic> kwics;

    /**
     * @param hits
     */
    Kwics(Hits hits, int contextSize) {
        this.hits = hits;
    
        // Get the concordances
        kwics = retrieveKwics(contextSize < 0 ? hits.settings().contextSize() : contextSize, hits.field());
    }

    /**
     * Return the KWIC for the specified hit.
     *
     * The first call to this method will fetch the KWICs for all the hits in this
     * Hits object. So make sure to select an appropriate HitsWindow first: don't
     * call this method on a Hits set with >1M hits unless you really want to
     * display all of them in one go.
     *
     * @param h the hit
     * @return KWIC for this hit
     */
    public Kwic get(Hit h) {
        if (kwics == null)
            throw new BlackLabException("Call findKwics() before getKwic().");
        return kwics.get(h);
    }

    
    /**
     * Retrieve KWICs for a (sub)list of hits.
     *
     * KWICs are the hit words 'centered' with a certain number of context words
     * around them.
     *
     * The size of the left and right context (in words) may be set using
     * Searcher.setConcordanceContextSize().
     *
     * @param contextSize how many words around the hit to retrieve
     * @param fieldName field to use for building KWICs
     *
     * @return the KWICs
     */
    private Map<Hit, Kwic> retrieveKwics(int contextSize, AnnotatedField field) {
        // Group hits per document
        MutableIntObjectMap<List<Hit>> hitsPerDocument = IntObjectMaps.mutable.empty();
        for (Hit key: this.hits) {
            List<Hit> hitsInDoc = hitsPerDocument.get(key.doc());
            if (hitsInDoc == null) {
                hitsInDoc = new ArrayList<>();
                hitsPerDocument.put(key.doc(), hitsInDoc);
            }
            hitsInDoc.add(key);
        }

        // All FIs except word and punct are attributes
        Map<Annotation, ForwardIndex> attrForwardIndices = new HashMap<>();
        for (Annotation annotation: field.annotations()) {
            if (annotation.hasForwardIndex() && !annotation.name().equals(Kwic.DEFAULT_CONC_WORD_PROP) && !annotation.name().equals(Kwic.DEFAULT_CONC_PUNCT_PROP)) {
                attrForwardIndices.put(annotation, this.hits.index().forwardIndex(annotation));
            }
        }
        ForwardIndex wordForwardIndex = this.hits.index().forwardIndex(field.annotations().get(Kwic.DEFAULT_CONC_WORD_PROP));
        ForwardIndex punctForwardIndex = this.hits.index().forwardIndex(field.annotations().get(Kwic.DEFAULT_CONC_PUNCT_PROP));
        Map<Hit, Kwic> conc1 = new HashMap<>();
        for (List<Hit> l : hitsPerDocument.values()) {
            Contexts.makeKwicsSingleDocForwardIndex(l, wordForwardIndex, punctForwardIndex, attrForwardIndices, contextSize, conc1);
        }
        return conc1;
    }
    
    
}
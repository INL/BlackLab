package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.IndexReader;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;

import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.FiidLookup;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/** KWICs for a list of hits. */
public class Kwics {
    
    /**
     * The KWIC data, if it has been retrieved.
     *
     * NOTE: this will always be null if not all the hits have been retrieved.
     */
    Map<Hit, Kwic> kwics;

    /**
     * @param hits
     */
    protected Kwics(Hits hits, ContextSize contextSize) {
        if (contextSize.left() < 0 || contextSize.right() < 0)
            throw new IllegalArgumentException("contextSize cannot be negative");
    
        // Get the concordances
        kwics = retrieveKwics(hits, contextSize, hits.field());
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
        return kwics.get(h);
    }

    
    /**
     * Retrieve KWICs for a (sub)list of hits.
     *
     * KWICs are the hit words 'centered' with a certain number of context words
     * around them.
     *
     * @param contextSize how many words around the hit to retrieve
     * @param fieldName field to use for building KWICs
     *
     * @return the KWICs
     */
    private static Map<Hit, Kwic> retrieveKwics(Hits hits, ContextSize contextSize, AnnotatedField field) {
        // Group hits per document
        MutableIntObjectMap<List<Hit>> hitsPerDocument = IntObjectMaps.mutable.empty();
        for (Hit key: hits) {
            List<Hit> hitsInDoc = hitsPerDocument.get(key.doc());
            if (hitsInDoc == null) {
                hitsInDoc = new ArrayList<>();
                hitsPerDocument.put(key.doc(), hitsInDoc);
            }
            hitsInDoc.add(key);
        }

        // All FIs except word and punct are attributes
        Map<Annotation, AnnotationForwardIndex> attrForwardIndices = new HashMap<>();
        BlackLabIndex index = hits.index();
        for (Annotation annotation: field.annotations()) {
            if (annotation.hasForwardIndex() && !annotation.name().equals(Kwic.DEFAULT_CONC_WORD_PROP) && !annotation.name().equals(Kwic.DEFAULT_CONC_PUNCT_PROP)) {
                attrForwardIndices.put(annotation, index.annotationForwardIndex(annotation));
            }
        }
        Annotation wordAnnot = field.annotation(Kwic.DEFAULT_CONC_WORD_PROP);
        AnnotationForwardIndex wordForwardIndex = index.annotationForwardIndex(wordAnnot);
        Annotation punctAnnot = field.annotation(Kwic.DEFAULT_CONC_PUNCT_PROP);
        AnnotationForwardIndex punctForwardIndex = index.annotationForwardIndex(punctAnnot);
        
        // Get FiidLookups for all required forward indexes
        IndexReader reader = hits.queryInfo().index().reader();
        Map<Annotation, FiidLookup> fiidLookups = new HashMap<>();
        fiidLookups.put(wordAnnot, new FiidLookup(reader, wordAnnot));
        fiidLookups.put(punctAnnot, new FiidLookup(reader, punctAnnot));
        for (Map.Entry<Annotation, AnnotationForwardIndex> e: attrForwardIndices.entrySet()) {
            fiidLookups.put(e.getKey(), new FiidLookup(reader, e.getKey()));
        }
        
        Map<Hit, Kwic> conc1 = new HashMap<>();
        
        /*
         * if doc not is last doc 
         *  process section if needed
         *  save start of new section
         *  
         * process end section
         */
        int lastDocId = -1;
        int firstIndexWithCurrentDocId = 0;
        for (int i = 1; i < hits.size(); ++i) {
            int curDocId = hits.hitsArrays().doc(i);
            if (curDocId != lastDocId) {
                if (firstIndexWithCurrentDocId != i) {
                    Contexts.makeKwicsSingleDocForwardIndex(
                        hits.window(firstIndexWithCurrentDocId, i - firstIndexWithCurrentDocId), 
                        wordForwardIndex, punctForwardIndex, attrForwardIndices, fiidLookups, contextSize, conc1);
                }
                firstIndexWithCurrentDocId = i;
                lastDocId = curDocId;
            }
        }
        // last part
        Contexts.makeKwicsSingleDocForwardIndex(
            hits.window(firstIndexWithCurrentDocId, hits.size() - firstIndexWithCurrentDocId), 
            wordForwardIndex, punctForwardIndex, attrForwardIndices, fiidLookups, contextSize, conc1);
        
        return conc1;
    }
    
}
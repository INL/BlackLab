package nl.inl.blacklab.search.results;

import java.util.HashMap;
import java.util.Map;

import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/** KWICs ("key words in context") for a list of hits.
 *
 * Instances of this class are immutable.
 */
public class Kwics {
    
    /**
     * The KWIC data, if it has been retrieved.
     *
     * NOTE: this will always be null if not all the hits have been retrieved.
     */
    Map<Hit, Kwic> kwics;

    /**
     */
    protected Kwics(Hits hits, ContextSize contextSize) {
        if (contextSize.before() < 0 || contextSize.after() < 0)
            throw new IllegalArgumentException("contextSize cannot be negative: " + contextSize);
    
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
     * KWICs ("key words in context") are the hit words 'centered' with a
     * certain number of context words around them.
     *
     * @param hits hits to retrieve kwics for
     * @param contextSize how many words around the hit to retrieve
     * @param field field to use for building KWICs
     *
     * @return the KWICs
     */
    private static Map<Hit, Kwic> retrieveKwics(Hits hits, ContextSize contextSize, AnnotatedField field) {
        // Group hits per document
//        MutableIntObjectMap<List<Hit>> hitsPerDocument = IntObjectMaps.mutable.empty();
//        for (Iterator<EphemeralHit> it = hits.ephemeralIterator(); it.hasNext(); ) {
//            EphemeralHit key = it.next();
//            List<Hit> hitsInDoc = hitsPerDocument.get(key.doc());
//            if (hitsInDoc == null) {
//                hitsInDoc = new ArrayList<>();
//                hitsPerDocument.put(key.doc(), hitsInDoc);
//            }
//            hitsInDoc.add(key);
//        }

        // All FIs except word and punct are attributes
        BlackLabIndex index = hits.index();
        Annotation wordAnnot = field.mainAnnotation();
        AnnotationForwardIndex wordForwardIndex = index.annotationForwardIndex(wordAnnot);
        Annotation punctAnnot = field.annotation(AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME);
        AnnotationForwardIndex punctForwardIndex = index.annotationForwardIndex(punctAnnot);
        Map<Annotation, AnnotationForwardIndex> attrForwardIndices = new HashMap<>();
        for (Annotation annotation: field.annotations()) {
            if (annotation.hasForwardIndex() && !annotation.equals(field.mainAnnotation()) && !annotation.name().equals(
                    AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME)) {
                attrForwardIndices.put(annotation, index.annotationForwardIndex(annotation));
            }
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
            int curDocId = hits.doc(i);
            if (curDocId != lastDocId) {
                if (firstIndexWithCurrentDocId != i) {
                    Contexts.makeKwicsSingleDocForwardIndex(
                        hits.window(firstIndexWithCurrentDocId, i - firstIndexWithCurrentDocId), 
                        wordForwardIndex, punctForwardIndex, attrForwardIndices, contextSize, conc1);
                }
                firstIndexWithCurrentDocId = i;
                lastDocId = curDocId;
            }
        }
        // last part
        Contexts.makeKwicsSingleDocForwardIndex(
            hits.window(firstIndexWithCurrentDocId, hits.size() - firstIndexWithCurrentDocId), 
            wordForwardIndex, punctForwardIndex, attrForwardIndices, contextSize, conc1);
        
        return conc1;
    }
    
}

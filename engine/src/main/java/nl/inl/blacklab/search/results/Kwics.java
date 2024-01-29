package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.ForwardIndex;
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
        // Collect FIs, with punct being the first and the main annotation (e.g. word) being the last.
        // (this convention originates from how we write our XML structure)
        ForwardIndex forwardIndex = hits.index().forwardIndex(field);
        List<AnnotationForwardIndex> forwardIndexes = new ArrayList<>(field.annotations().size());
        forwardIndexes.add(forwardIndex.get(field.annotation(AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME)));
        for (Annotation annotation: field.annotations()) {
            if (annotation.hasForwardIndex() && !annotation.equals(field.mainAnnotation()) && !annotation.name().equals(
                    AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME)) {
                forwardIndexes.add(forwardIndex.get(annotation));
            }
        }
        forwardIndexes.add(forwardIndex.get(field.mainAnnotation()));

        // Iterate over hits and fetch KWICs per document
        int lastDocId = -1;
        int firstIndexWithCurrentDocId = 0;
        Map<Hit, Kwic> kwics = new HashMap<>();
        for (int i = 0; i < hits.size(); ++i) {
            int curDocId = hits.doc(i);
            if (lastDocId != -1 && curDocId != lastDocId) {
                // We've reached a new document, so process the previous one
                Contexts.makeKwicsSingleDocForwardIndex(
                        hits.window(firstIndexWithCurrentDocId, i - firstIndexWithCurrentDocId),
                        forwardIndexes, contextSize, kwics);
                firstIndexWithCurrentDocId = i; // remember start of the new document
            }
            lastDocId = curDocId;
        }
        // Last document
        Contexts.makeKwicsSingleDocForwardIndex(
                hits.window(firstIndexWithCurrentDocId, hits.size() - firstIndexWithCurrentDocId),
                forwardIndexes, contextSize, kwics);

        return kwics;
    }
    
}

package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.RelationListInfo;

/** KWICs ("key words in context") for a list of hits.
 *
 * Instances of this class are immutable.
 */
public class Kwics {
    
    /** The KWIC data. */
    private final Map<Hit, Kwic> kwics;

    /** KWICs in other fields (for parallel corpora), or null if none. */
    private Map<Hit, Map<String, Kwic>> foreignKwics = null;

    public Kwics(Hits hits, ContextSize contextSize) {
        if (contextSize.before() < 0 || contextSize.after() < 0)
            throw new IllegalArgumentException("contextSize cannot be negative: " + contextSize);
    
        // Get the concordances
        kwics = retrieveKwics(hits, contextSize, hits.field());

        // Get the concordances for other fields (for parallel corpora), if there are any
        foreignKwics = retrieveForeignKwics(hits, contextSize);
    }

    private Map<Hit, Map<String, Kwic>> retrieveForeignKwics(Hits hits, ContextSize contextSize) {
        Map<Hit, Map<String, Kwic>> foreignKwics = null;
        Map<String, List<AnnotationForwardIndex>> afisPerField = new HashMap<>();;
        String defaultField = hits.field().name();
        for (Iterator<EphemeralHit> it = hits.ephemeralIterator(); it.hasNext(); ) {
            EphemeralHit hit = it.next();
            Map<String, int[]> minMaxPerField = null; // what context span we need for each field
            MatchInfo[] matchInfo = hit.matchInfo();
            if (matchInfo != null) {
                for (MatchInfo mi: matchInfo) {
                    if (mi == null)
                        continue; // not captured for this hit
                    minMaxPerField = updateMinMaxForMatchInfo(hits.index(), mi, defaultField, minMaxPerField,
                            afisPerField);
                }

                if (minMaxPerField != null) {
                    Map<String, Kwic> kwics = new HashMap<>();
                    ContextSize noContext = ContextSize.get(0, 0, true,
                            contextSize.getMaxSnippetLength());
                    for (Map.Entry<String, int[]> e: minMaxPerField.entrySet()) {
                        int[] minMax = e.getValue();
                        int snippetStart = Math.max(0, minMax[0] - contextSize.before());
                        int snippetEnd = minMax[1] + contextSize.after();
                        if (snippetEnd - snippetStart > contextSize.getMaxSnippetLength())
                            snippetEnd = snippetStart + contextSize.getMaxSnippetLength();

                        String field = e.getKey();
                        List<AnnotationForwardIndex> afis = afisPerField.get(field);
                        Hits singleHit = Hits.singleton(hits.queryInfo(), hit.doc(), snippetStart, snippetEnd);
                        Contexts.makeKwicsSingleDocForwardIndex(singleHit, afis, noContext,
                                (__, kwic) -> kwics.put(field, kwic));
                    }
                    if (foreignKwics == null)
                        foreignKwics = new HashMap<>();
                    foreignKwics.put(hit.toHit(), kwics);
                }
            }
        }
        return foreignKwics;
    }

    private static Map<String, int[]> updateMinMaxForMatchInfo(BlackLabIndex index, MatchInfo mi, String defaultField,
            Map<String, int[]> minMaxPerField, Map<String, List<AnnotationForwardIndex>> afisPerField) {
        String field = mi.getField();
        if (!field.equals(defaultField)) { // foreign KWICs only
            minMaxPerField = updateMinMaxPerField(minMaxPerField, field, mi.getSpanStart(), mi.getSpanEnd());
            afisPerField.computeIfAbsent(field, k -> getAnnotationForwardIndexes(
                    index.forwardIndex(index.annotatedField(field))));
        }
        if (mi instanceof RelationInfo) {
            // Relation targets (not just sources) should also influence field context
            RelationInfo rmi = (RelationInfo) mi;
            String tfield = rmi.getTargetField() == null ? field : rmi.getTargetField();
            if (!tfield.equals(defaultField)) { // foreign KWICs only
                minMaxPerField = updateMinMaxPerField(minMaxPerField, tfield, rmi.getTargetStart(), rmi.getTargetEnd());
                afisPerField.computeIfAbsent(tfield, k ->
                        getAnnotationForwardIndexes(
                                index.forwardIndex(index.annotatedField(tfield))));
            }
        } else if (mi instanceof RelationListInfo) {
            RelationListInfo l = (RelationListInfo) mi;
            for (RelationInfo rmi: l.getRelations()) {
                minMaxPerField = updateMinMaxForMatchInfo(index, rmi, defaultField, minMaxPerField, afisPerField);
            }
        }
        return minMaxPerField;
    }

    private static Map<String, int[]> updateMinMaxPerField(Map<String, int[]> minMaxPerField, String field,
            int start, int end) {
        // Keep track of the min/max positions of the match in each foreign field
        if (minMaxPerField == null)
            minMaxPerField = new HashMap<>();
        minMaxPerField.compute(field, (k, v) -> {
            if (v == null) {
                return new int[] { start, end };
            } else {
                v[0] = Math.min(v[0], start);
                v[1] = Math.max(v[1], end);
                return v;
            }
        });
        return minMaxPerField;
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
     * Return the foreign KWICs for a hit, if any.
     *
     * Foerign KWICs are KWICs in another than the primary field. This only
     * applies to parallel corpora.
     *
     * @param hit the hit
     * @return foreign KWICs for this hit, or null if none
     */
    public Map<String, Kwic> getForeignKwics(Hit hit) {
        return foreignKwics == null ? null : foreignKwics.get(hit);
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
        List<AnnotationForwardIndex> forwardIndexes = getAnnotationForwardIndexes(forwardIndex);

        // Iterate over hits and fetch KWICs per document
        int lastDocId = -1;
        int firstIndexWithCurrentDocId = 0;
        Map<Hit, Kwic> kwics = new HashMap<>();
        BiConsumer<Hit, Kwic> kwicAdder = (hit, kwic) -> kwics.put(hit, kwic);
        for (int i = 0; i < hits.size(); ++i) {
            int curDocId = hits.doc(i);
            if (lastDocId != -1 && curDocId != lastDocId) {
                // We've reached a new document, so process the previous one
                Contexts.makeKwicsSingleDocForwardIndex(
                        hits.window(firstIndexWithCurrentDocId, i - firstIndexWithCurrentDocId),
                        forwardIndexes, contextSize, kwicAdder);
                firstIndexWithCurrentDocId = i; // remember start of the new document
            }
            lastDocId = curDocId;
        }
        // Last document
        Contexts.makeKwicsSingleDocForwardIndex(
                hits.window(firstIndexWithCurrentDocId, hits.size() - firstIndexWithCurrentDocId),
                forwardIndexes, contextSize, kwicAdder);

        return kwics;
    }

    private static List<AnnotationForwardIndex> getAnnotationForwardIndexes(ForwardIndex forwardIndex) {
        AnnotatedField field = forwardIndex.field();
        List<AnnotationForwardIndex> forwardIndexes = new ArrayList<>(field.annotations().size());
        forwardIndexes.add(forwardIndex.get(field.annotation(AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME)));
        for (Annotation annotation: field.annotations()) {
            if (annotation.hasForwardIndex() && !annotation.equals(field.mainAnnotation()) && !annotation.name().equals(
                    AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME)) {
                forwardIndexes.add(forwardIndex.get(annotation));
            }
        }
        forwardIndexes.add(forwardIndex.get(field.mainAnnotation()));
        return forwardIndexes;
    }
}

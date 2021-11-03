package nl.inl.blacklab.search.results;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;

import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.Doc;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.results.Hits.HitsArrays;
import nl.inl.util.XmlHighlighter;

/** Concordances for a list of hits. */
public class Concordances {

    /**
     * The concordances, if they have been retrieved.
     *
     * NOTE: when making concordances from the forward index, this will always be
     * null, because Kwics will be used internally. This is only used when making
     * concordances from the content store (the old default).
     */
    private Map<Hit, Concordance> concordances;
    
    Kwics kwics = null;

    /**
     * @param hits
     */
    public Concordances(Hits hits, ConcordanceType type, ContextSize contextSize) {
        if (contextSize.left() < 0 || contextSize.right() < 0)
            throw new IllegalArgumentException("contextSize cannot be negative");
        if (type == ConcordanceType.FORWARD_INDEX) {
            kwics = new Kwics(hits, contextSize);
        }
    
        // Get the concordances
        concordances = retrieveConcordancesFromContentStore(hits, contextSize);
    }

    /**
     * Return the concordance for the specified hit.
     *
     * The first call to this method will fetch the concordances for all the hits in
     * this Hits object. So make sure to select an appropriate HitsWindow first:
     * don't call this method on a Hits set with >1M hits unless you really want to
     * display all of them in one go.
     *
     * @param h the hit
     * @return concordance for this hit
     */
    public Concordance get(Hit h) {
        if (kwics != null)
            return kwics.get(h).toConcordance();
        return concordances.get(h);
    }

    /**
     * Retrieves the concordance information (left, hit and right context) for a
     * number of hits in the same document from the ContentStore.
     *
     * NOTE1: it is assumed that all hits in this Hits object are in the same
     * document!
     * 
     * @param hits hits to make concordance for
     * @param field field to make conc for
     * @param wordsAroundHit number of words left and right of hit to fetch
     * @param conc where to add the concordances
     * @param hl
     */
    private synchronized static void makeConcordancesSingleDocContentStore(Hits hits, ContextSize wordsAroundHit,
            Map<Hit, Concordance> conc,
            XmlHighlighter hl) {
        if (hits.size() == 0)
            return;
        QueryInfo queryInfo = hits.queryInfo();
        Doc doc = queryInfo.index().doc(hits.get(0).doc());
        int arrayLength = hits.size() * 2;
        int[] startsOfWords = new int[arrayLength];
        int[] endsOfWords = new int[arrayLength];

        // Determine the first and last word of the concordance, as well as the
        // first and last word of the actual hit inside the concordance.
        int startEndArrayIndex = 0;
        for (Hit hit : hits) {
            int hitStart = hit.start();
            int hitEnd = hit.end() - 1;

            int start = hitStart - wordsAroundHit.left();
            if (start < 0)
                start = 0;
            int end = hitEnd + wordsAroundHit.right();

            startsOfWords[startEndArrayIndex] = start;
            startsOfWords[startEndArrayIndex + 1] = hitStart;
            endsOfWords[startEndArrayIndex] = hitEnd;
            endsOfWords[startEndArrayIndex + 1] = end;

            startEndArrayIndex += 2;
        }

        // Get the relevant character offsets (overwrites the startsOfWords and endsOfWords
        // arrays)
        AnnotatedField field = queryInfo.field();
        doc.characterOffsets(field, startsOfWords, endsOfWords, true);

        // Make all the concordances
        List<Concordance> newConcs = doc.makeConcordancesFromContentStore(field, startsOfWords, endsOfWords, hl);
        for (int i = 0; i < hits.size(); i++) {
            conc.put(hits.get(i), newConcs.get(i));
        }
    }

    /**
     * Generate concordances from content store (slower).
     *
     * @param hits hits for which to generate concordances
     * @param contextSize how many words around the hit to retrieve
     * @return the concordances
     */
    private static Map<Hit, Concordance> retrieveConcordancesFromContentStore(Hits hits, ContextSize contextSize) {
        XmlHighlighter hl = new XmlHighlighter(); // used to make fragments well-formed
        QueryInfo queryInfo = hits.queryInfo();
        hl.setUnbalancedTagsStrategy(queryInfo.index().defaultUnbalancedTagsStrategy());
        // Group hits per document
        MutableIntObjectMap<HitsArrays> hitsPerDocument = IntObjectMaps.mutable.empty();
        for (Hit key: hits) {
            HitsArrays hitsInDoc = hitsPerDocument.get(key.doc());
            if (hitsInDoc == null) {
                hitsInDoc = new HitsArrays();
                hitsPerDocument.put(key.doc(), hitsInDoc);
            }
            hitsInDoc.add(key);
        }
        Map<Hit, Concordance> conc = new HashMap<>();
        for (HitsArrays l : hitsPerDocument.values()) {
            Hits hitsInThisDoc = Hits.fromList(queryInfo, l, null);
            Concordances.makeConcordancesSingleDocContentStore(hitsInThisDoc, contextSize, conc, hl);
        }
        return conc;
    }
    
}
package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.Doc;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.Field;
import nl.inl.util.XmlHighlighter;

/**
 * Methods for creating Kwics and Concordances from hits.
 */
public class HitDisplay {
    
    /** Our hits object */
    private final Hits hits;

    /**
     * The KWIC data, if it has been retrieved.
     *
     * NOTE: this will always be null if not all the hits have been retrieved.
     */
    Map<Hit, Kwic> kwics;

    /**
     * The concordances, if they have been retrieved.
     *
     * NOTE: when making concordances from the forward index, this will always be
     * null, because Kwics will be used internally. This is only used when making
     * concordances from the content store (the old default).
     */
    private Map<Hit, Concordance> concordances;

    /**
     * @param hits
     */
    HitDisplay(Hits hits) {
        this.hits = hits;
    }

    /**
     * Retrieve KWICs for the hits.
     * @param contextSize desired context size, or less than zero for default
     */
    public synchronized void findKwics(int contextSize) {
        try {
            this.hits.ensureAllHitsRead();
        } catch (InterruptedException e) {
            // Thread was interrupted. Just go ahead with the hits we did
            // get, so at least we'll have valid concordances.
            Thread.currentThread().interrupt();
        }
        // Make sure we don't have the desired concordances already
        if (kwics != null) {
            return;
        }
    
        // Get the concordances
        kwics = retrieveKwics(contextSize < 0 ? this.hits.settings().contextSize() : contextSize, this.hits.field());
    }

    /**
     * Retrieve concordances for the hits.
     *
     * You shouldn't have to call this manually, as it's automatically called when
     * you call getConcordance() for the first time.
     * 
     * @param contextSize desired context size, or less than zero for default
     */
    public synchronized void findConcordances(int contextSize) {
        if (this.hits.settings.concordanceType() == ConcordanceType.FORWARD_INDEX) {
            findKwics(contextSize);
            return;
        }
    
        try {
            this.hits.ensureAllHitsRead();
        } catch (InterruptedException e) {
            // Thread was interrupted. Just go ahead with the hits we did
            // get, so at least we'll have valid concordances.
            Thread.currentThread().interrupt();
        }
        // Make sure we don't have the desired concordances already
        if (concordances != null) {
            return;
        }
    
        // Get the concordances
        concordances = retrieveConcordancesFromContentStore(contextSize < 0 ? this.hits.settings().contextSize() : contextSize, this.hits.field());
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
    public Concordance getConcordance(Hit h) {
        if (this.hits.settings.concordanceType() == ConcordanceType.FORWARD_INDEX)
            return getKwic(h).toConcordance();
        if (concordances == null)
            throw new BlackLabException("Call findConcordances() before getConcordance().");
        return concordances.get(h);
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
    public Kwic getKwic(Hit h) {
        if (kwics == null)
            throw new BlackLabException("Call findKwics() before getKwic().");
        return kwics.get(h);
    }

    /**
     * Retrieves the concordance information (left, hit and right context) for a
     * number of hits in the same document from the ContentStore.
     *
     * NOTE1: it is assumed that all hits in this Hits object are in the same
     * document!
     * 
     * @param field field to make conc for
     * @param wordsAroundHit number of words left and right of hit to fetch
     * @param conc where to add the concordances
     * @param hl
     */
    private synchronized void makeConcordancesSingleDocContentStore(Field field, int wordsAroundHit,
            Map<Hit, Concordance> conc,
            XmlHighlighter hl) {
        if (this.hits.hits.isEmpty())
            return;
        Doc doc = this.hits.index.doc(this.hits.hits.get(0).doc());
        int arrayLength = this.hits.hits.size() * 2;
        int[] startsOfWords = new int[arrayLength];
        int[] endsOfWords = new int[arrayLength];

        // Determine the first and last word of the concordance, as well as the
        // first and last word of the actual hit inside the concordance.
        int startEndArrayIndex = 0;
        for (Hit hit : this.hits.hits) {
            int hitStart = hit.start();
            int hitEnd = hit.end() - 1;

            int start = hitStart - wordsAroundHit;
            if (start < 0)
                start = 0;
            int end = hitEnd + wordsAroundHit;

            startsOfWords[startEndArrayIndex] = start;
            startsOfWords[startEndArrayIndex + 1] = hitStart;
            endsOfWords[startEndArrayIndex] = hitEnd;
            endsOfWords[startEndArrayIndex + 1] = end;

            startEndArrayIndex += 2;
        }

        // Get the relevant character offsets (overwrites the startsOfWords and endsOfWords
        // arrays)
        doc.getCharacterOffsets(field, startsOfWords, endsOfWords, true);

        // Make all the concordances
        List<Concordance> newConcs = doc.makeConcordancesFromContentStore(field, startsOfWords, endsOfWords, hl);
        for (int i = 0; i < this.hits.hits.size(); i++) {
            conc.put(this.hits.hits.get(i), newConcs.get(i));
        }
    }

    /**
     * Generate concordances from content store (slower).
     *
     * @param contextSize how many words around the hit to retrieve
     * @param fieldName field to use for building concordances
     * @return the concordances
     */
    private Map<Hit, Concordance> retrieveConcordancesFromContentStore(int contextSize, AnnotatedField field) {
        XmlHighlighter hl = new XmlHighlighter(); // used to make fragments well-formed
        hl.setUnbalancedTagsStrategy(this.hits.index.defaultUnbalancedTagsStrategy());
        // Group hits per document
        MutableIntObjectMap<List<Hit>> hitsPerDocument = IntObjectMaps.mutable.empty();
        for (Hit key : this.hits.hits) {
            List<Hit> hitsInDoc = hitsPerDocument.get(key.doc());
            if (hitsInDoc == null) {
                hitsInDoc = new ArrayList<>();
                hitsPerDocument.put(key.doc(), hitsInDoc);
            }
            hitsInDoc.add(key);
        }
        Map<Hit, Concordance> conc = new HashMap<>();
        for (List<Hit> l : hitsPerDocument.values()) {
            Hits hitsInThisDoc = new Hits(this.hits.index, field, l, this.hits.settings);
            hitsInThisDoc.copyMaxAndContextFrom(this.hits);
            hitsInThisDoc.hitDisplay().makeConcordancesSingleDocContentStore(field, contextSize, conc, hl);
        }
        return conc;
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
                attrForwardIndices.put(annotation, this.hits.index.forwardIndex(annotation));
            }
        }
        ForwardIndex wordForwardIndex = this.hits.index.forwardIndex(field.annotations().get(Kwic.DEFAULT_CONC_WORD_PROP));
        ForwardIndex punctForwardIndex = this.hits.index.forwardIndex(field.annotations().get(Kwic.DEFAULT_CONC_PUNCT_PROP));
        Map<Hit, Kwic> conc1 = new HashMap<>();
        for (List<Hit> l : hitsPerDocument.values()) {
            Contexts.makeKwicsSingleDocForwardIndex(l, wordForwardIndex, punctForwardIndex, attrForwardIndices, contextSize, conc1);
        }
        return conc1;
    }
    
    
}
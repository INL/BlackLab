package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.FiidLookup;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.results.Hits.EphemeralHit;
import nl.inl.blacklab.search.results.Hits.HitsArrays;

public class Contexts implements Iterable<int[]> {

    /** In context arrays, how many bookkeeping ints are stored at the start? */
    public final static int NUMBER_OF_BOOKKEEPING_INTS = 3;

    /**
     * In context arrays, what index after the bookkeeping units indicates the hit
     * start?
     */
    public final static int HIT_START_INDEX = 0;

    /**
     * In context arrays, what index indicates the hit end (start of right part)?
     */
    public final static int RIGHT_START_INDEX = 1;

    /** In context arrays, what index indicates the length of the context? */
    public final static int LENGTH_INDEX = 2;

    // Instance variables
    //------------------------------------------------------------------------------

    /**
     * Retrieves the KWIC information (KeyWord In Context: left, hit and right
     * context) for a number of hits in the same document from the ContentStore.
     *
     * NOTE: this destroys any existing contexts!
     *
     * @param forwardIndex Forward index for the words
     * @param punctForwardIndex Forward index for the punctuation
     * @param attrForwardIndices Forward indices for the attributes, or null if none
     * @param wordsAroundHit number of words left and right of hit to fetch
     * @param theKwics where to add the KWICs
     */
    static void makeKwicsSingleDocForwardIndex(
                                               Hits hits,
                                               AnnotationForwardIndex forwardIndex,
                                               AnnotationForwardIndex punctForwardIndex,
                                               Map<Annotation, AnnotationForwardIndex> attrForwardIndices,
                                               Map<Annotation, FiidLookup> fiidLookups,
                                               ContextSize wordsAroundHit,
                                               Map<Hit, Kwic> theKwics
                                               ) {
        if (hits.size() == 0)
            return;

        // TODO: more efficient to get all contexts with one getContextWords() call!

        // Get punctuation context
        int[][] punctContext = null;
        if (punctForwardIndex != null) {
            punctContext = getContextWordsSingleDocument(hits.hitsArrays, 0, hits.size(), wordsAroundHit, Arrays.asList(punctForwardIndex), Arrays.asList(fiidLookups.get(punctForwardIndex.annotation())));
        }
        Terms punctTerms = punctForwardIndex == null ? null : punctForwardIndex.terms();

        // Get attributes context
        Annotation[] attrName = null;
        Terms[] attrTerms = null;
        int[][][] attrContext = null;
        if (attrForwardIndices != null) {
            int n = attrForwardIndices.size();
            attrName = new Annotation[n];
            AnnotationForwardIndex[] attrFI = new AnnotationForwardIndex[n];
            attrTerms = new Terms[n];
            attrContext = new int[n][][];
            int i = 0;
            for (Map.Entry<Annotation, AnnotationForwardIndex> e: attrForwardIndices.entrySet()) {
                attrName[i] = e.getKey();
                attrFI[i] = e.getValue();
                attrTerms[i] = attrFI[i].terms();
                attrContext[i] = getContextWordsSingleDocument(hits.hitsArrays, 0, hits.size(), wordsAroundHit, Arrays.asList(attrFI[i]), Arrays.asList(fiidLookups.get(attrName[i])));
                i++;
            }
        }

        // Get word context
        int[][] wordContext = getContextWordsSingleDocument(hits.hitsArrays, 0, hits.size(), wordsAroundHit, Arrays.asList(forwardIndex), Arrays.asList(fiidLookups.get(forwardIndex.annotation())));
        Terms terms = forwardIndex.terms();

        // Make the concordances from the context
        AnnotatedField field = forwardIndex.annotation().field();
        Annotation concPunctFI = field.annotation(Kwic.DEFAULT_CONC_PUNCT_PROP);
        Annotation concWordFI = field.annotation(Kwic.DEFAULT_CONC_WORD_PROP);
        for (int i = 0; i < hits.size(); i++) {
            Hit h = hits.get(i);
            List<String> tokens = new ArrayList<>();
            int[] context = wordContext[i];
            int contextLength = context[Contexts.LENGTH_INDEX];
            int contextRightStart = context[Contexts.RIGHT_START_INDEX];
            int contextHitStart = context[Contexts.HIT_START_INDEX];
            int indexInContext = Contexts.NUMBER_OF_BOOKKEEPING_INTS;
            for (int j = 0; j < contextLength; j++, indexInContext++) {

                // Add punctuation before word
                // (Applications may choose to ignore punctuation before the first word)
                if (punctTerms == null) {
                    // There is no punctuation forward index. Just put a space
                    // between every word.
                    tokens.add(" ");
                } else
                    tokens.add(punctTerms.get(punctContext[i][indexInContext]));

                // Add extra attributes (e.g. lemma, pos)
                if (attrContext != null) {
                    for (int k = 0; k < attrContext.length; k++) {
                        tokens.add(attrTerms[k].get(attrContext[k][i][indexInContext]));
                    }
                }

                // Add word
                if (terms != null)
                    tokens.add(terms.get(context[indexInContext]));
                else
                    tokens.add(""); // weird, but make sure the numbers add up at the end

            }
            List<Annotation> annotations = new ArrayList<>();
            annotations.add(concPunctFI);
            if (attrContext != null) {
                annotations.addAll(Arrays.asList(attrName));
            }
            annotations.add(concWordFI);
            Kwic kwic = new Kwic(annotations, tokens, contextHitStart, contextRightStart);
            theKwics.put(h, kwic);
        }
    }

    /**
     * Get context words from the forward index.
     *
     * @param hits the hits
     * @param start inclusive
     * @param end exclusive
     * @param contextSize how many words of context we want
     * @param contextSources forward indices to get context from
     * @param fiidLookups how to find the forward index ids of documents
     */
    private static int[][] getContextWordsSingleDocument(HitsArrays hits, int start, int end, ContextSize contextSize,
            List<AnnotationForwardIndex> contextSources, List<FiidLookup> fiidLookups) {
        final int n = end - start;
        if (n == 0)
            return new int[0][];
        int[] startsOfSnippets = new int[n];
        int[] endsOfSnippets = new int[n];

        EphemeralHit hit = new EphemeralHit();
        for (int i = start; i < end; ++i) {
            hits.getEphemeral(i, hit);
            startsOfSnippets[i - start] = Math.max(0, hit.start - contextSize.left());
            endsOfSnippets[i - start] = hit.end + contextSize.right();
        }

        int fiNumber = 0;
        int doc = hits.doc(start);
        int[][] contexts = new int[n][];
        for (AnnotationForwardIndex forwardIndex: contextSources) {
            FiidLookup fiidLookup = fiidLookups.get(fiNumber);
            // Get all the words from the forward index
            List<int[]> words;
            if (forwardIndex != null) {
                // We have a forward index for this field. Use it.
                int fiid = fiidLookup.get(doc);
                words = forwardIndex.retrievePartsInt(fiid, startsOfSnippets, endsOfSnippets);
            } else {
                throw new BlackLabRuntimeException("Cannot get context without a forward index");
            }

            // Build the actual concordances
//            int hitNum = 0;
            for (int i = 0; i < n; ++i) {
                int hitIndex = start + i;
                int[] theseWords = words.get(i);
                hits.getEphemeral(hitIndex, hit);

                int firstWordIndex = startsOfSnippets[i];

                if (fiNumber == 0) {
                    // Allocate context array and set hit and right start and context length
                    contexts[i] = new int[NUMBER_OF_BOOKKEEPING_INTS
                            + theseWords.length * contextSources.size()];
                    contexts[i][HIT_START_INDEX] = hit.start - firstWordIndex;
                    contexts[i][RIGHT_START_INDEX] = hit.end - firstWordIndex;
                    contexts[i][LENGTH_INDEX] = theseWords.length;
                }
                // Copy the context we just retrieved into the context array
                int copyStart = fiNumber * theseWords.length + NUMBER_OF_BOOKKEEPING_INTS;
                System.arraycopy(theseWords, 0, contexts[i], copyStart, theseWords.length);
            }

            fiNumber++;
        }
        return contexts;
    }

    /**
     * The hit contexts.
     *
     * There may be multiple contexts for each hit. Each
     * int array starts with three bookkeeping integers, followed by the contexts
     * information. The bookkeeping integers are:
     * 0 = hit start, index of the hit word (and length of the left context), counted from the start the context
     * 1 = right start, start of the right context, counted from the start the context
     * 2 = context length, length of 1 context. As stated above, there may be multiple contexts.
     *
     * The first context therefore starts at index 3.
     */
    private List<int[]> contexts;

    /**
     * If we have context information, this specifies the annotation(s) (i.e. word,
     * lemma, pos) the context came from. Otherwise, it is null.
     */
    private List<Annotation> annotations;

    // Methods that read data
    //------------------------------------------------------------------------------

    /**
     * Return a new Contexts instance that only includes the specified annotations
     * in the specified order.
     *
     * @param annotations annotations we want
     */
    @SuppressWarnings("unused")
    private Contexts(Contexts source, List<Annotation> annotations) {
        // Determine which contexts we want
        List<Integer> contextsToSelect = new ArrayList<>();
        for (int i = 0; i < source.annotations.size(); i++) {
            if (annotations.contains(source.annotations.get(i)))
                contextsToSelect.add(i);
        }
        if (contextsToSelect.size() < annotations.size())
            throw new BlackLabRuntimeException("Not all requested contexts were present");

        // Copy only the requested contexts
        int numberOfHits = source.contexts.size();
        contexts = new ArrayList<>(numberOfHits);

        for (int i = 0; i < source.contexts.size(); ++i) {
            int[] context = source.contexts.get(i);
            int hitContextLength = (context.length - NUMBER_OF_BOOKKEEPING_INTS)
                    / source.annotations.size();
            int[] result = new int[NUMBER_OF_BOOKKEEPING_INTS + hitContextLength * annotations.size()];
            System.arraycopy(context, 0, result, 0, NUMBER_OF_BOOKKEEPING_INTS);
            int resultContextNumber = 0;
            for (Integer sourceContextNumber: contextsToSelect) {
                System.arraycopy(context,
                        NUMBER_OF_BOOKKEEPING_INTS + sourceContextNumber * hitContextLength, result,
                        NUMBER_OF_BOOKKEEPING_INTS + resultContextNumber * hitContextLength,
                        NUMBER_OF_BOOKKEEPING_INTS);
                resultContextNumber++;
            }
            contexts.add(result);
        }
        this.annotations = annotations;
    }

    /**
     * Retrieve context words for the hits.
     *
     * @param hits hits to find contexts for
     * @param annotations the field and annotations to use for the context
     * @param contextSize how large the contexts need to be
     * @param fiidLookups how to look up the fiids for each annotation
     */
    public Contexts(Hits hits, List<Annotation> annotations, ContextSize contextSize, List<FiidLookup> fiidLookups) {
        if (annotations == null || annotations.isEmpty())
            throw new IllegalArgumentException("Cannot build contexts without annotations");

        hits.ensureAllResultsRead(); // make sure all hits have been read
        List<AnnotationForwardIndex> fis = new ArrayList<>();
        for (Annotation annotation: annotations) {
            fis.add(hits.index().annotationForwardIndex(annotation));
        }

        // Get the context
        // Group hits per document

        // setup first iteration
        HitsArrays ha = hits.hitsArrays;
        final int size = ha.size(); // TODO ugly, might be slow because of required locking
        int prevDoc = size == 0 ? -1 : ha.doc(0);
        int firstHitInCurrentDoc = 0;
        contexts = new ArrayList<>(hits.size());

        if (size > 0) {
            for (int i = 1; i < size; ++i) { // start at 1: variables already have correct values for primed for hit 0
                final int curDoc = ha.doc(i);
                if (curDoc != prevDoc) {
                    try { hits.threadAborter().checkAbort(); } catch (InterruptedException e) { throw new InterruptedSearch(e); }
                    // process hits in this document:
                    int[][] docContextArray = getContextWordsSingleDocument(ha, firstHitInCurrentDoc, i, contextSize, fis, fiidLookups);
                    for (int[] contextForHit : docContextArray) { contexts.add(contextForHit); }
                    // start a new document
                    firstHitInCurrentDoc = i;
                }
            }
            // Process trailing hits
            int[][] docContextArray = getContextWordsSingleDocument(ha, firstHitInCurrentDoc, hits.size(), contextSize, fis, fiidLookups);
            for (int[] contextForHit : docContextArray) { contexts.add(contextForHit); }
        }

        this.annotations = new ArrayList<>(annotations);
    }

    /**
     * Get the field our current concordances were retrieved from
     *
     * @return the field name
     */
    public List<Annotation> annotations() {
        return annotations;
    }

    /**
     * Return the context(s) for the specified hit number
     *
     * @param hit which hit we want the context(s) for
     * @return the context(s)
     */
    public int[] get(int index) {
        return contexts.get(index);
    }

    public int size() {
        return contexts.size();
    }

    /**
     * Iterate over the context arrays.
     *
     * Note that the order is unspecified.
     *
     * @return iterator
     */
    @Override
    public Iterator<int[]> iterator() {
        return contexts.iterator();
    }

    @Override
    public String toString() {
        return "Contexts(" + StringUtils.join(annotations, ", ") + ")";
    }

}

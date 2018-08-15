package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;

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
    static void makeKwicsSingleDocForwardIndex(List<Hit> hits, AnnotationForwardIndex forwardIndex,
            AnnotationForwardIndex punctForwardIndex, Map<Annotation, AnnotationForwardIndex> attrForwardIndices,
            ContextSize wordsAroundHit, Map<Hit, Kwic> theKwics) {
        if (hits.isEmpty())
            return;
    
        // TODO: more efficient to get all contexts with one getContextWords() call!
    
        // Get punctuation context
        int[][] punctContext = null;
        if (punctForwardIndex != null) {
            punctContext = getContextWordsSingleDocument(hits, wordsAroundHit, Arrays.asList(punctForwardIndex));
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
                attrContext[i] = getContextWordsSingleDocument(hits, wordsAroundHit, Arrays.asList(attrFI[i]));
                i++;
            }
        }
    
        // Get word context
        int[][] wordContext = getContextWordsSingleDocument(hits, wordsAroundHit, Arrays.asList(forwardIndex));
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
     * @param contextSize how many words of context we want
     * @param contextSources forward indices to get context from
     */
    private static int[][] getContextWordsSingleDocument(List<Hit> list, ContextSize contextSize,
            List<AnnotationForwardIndex> contextSources) {
        int n = list.size();
        if (n == 0)
            return new int[0][];
        int[] startsOfSnippets = new int[n];
        int[] endsOfSnippets = new int[n];
        int i = 0;
        for (Hit h: list) {
            int contextSz = contextSize.left();
            startsOfSnippets[i] = contextSz >= h.start() ? 0 : h.start() - contextSz;
            endsOfSnippets[i] = h.end() + contextSz;
            i++;
        }
    
        int fiNumber = 0;
        int doc = list.get(0).doc();
        int[][] contexts = new int[list.size()][];
        for (AnnotationForwardIndex forwardIndex: contextSources) {
            // Get all the words from the forward index
            List<int[]> words;
            if (forwardIndex != null) {
                // We have a forward index for this field. Use it.
                words = forwardIndex.retrievePartsInt(doc, startsOfSnippets, endsOfSnippets);
            } else {
                throw new BlackLabRuntimeException("Cannot get context without a forward index");
            }
    
            // Build the actual concordances
            Iterator<int[]> wordsIt = words.iterator();
            int hitNum = 0;
            for (Hit hit: list) {
                int[] theseWords = wordsIt.next();
    
                int firstWordIndex = startsOfSnippets[hitNum];
    
                if (fiNumber == 0) {
                    // Allocate context array and set hit and right start and context length
                    contexts[hitNum] = new int[NUMBER_OF_BOOKKEEPING_INTS
                            + theseWords.length * contextSources.size()];
                    contexts[hitNum][HIT_START_INDEX] = hit.start() - firstWordIndex;
                    contexts[hitNum][RIGHT_START_INDEX] = hit.end() - firstWordIndex;
                    contexts[hitNum][LENGTH_INDEX] = theseWords.length;
                }
                // Copy the context we just retrieved into the context array
                int start = fiNumber * theseWords.length + NUMBER_OF_BOOKKEEPING_INTS;
                System.arraycopy(theseWords, 0, contexts[hitNum], start, theseWords.length);
                hitNum++;
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
     * information. The bookkeeping integers are: * 0 = hit start, index of the hit
     * word (and length of the left context), counted from the start the context * 1
     * = right start, start of the right context, counted from the start the context
     * * 2 = context length, length of 1 context. As stated above, there may be
     * multiple contexts.
     *
     * The first context therefore starts at index 3.
     *
     */
    private Map<Hit, int[]> contexts;

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
        contexts = new IdentityHashMap<>(numberOfHits);
        for (Entry<Hit, int[]> e: source.contexts.entrySet()) {
            int[] context = e.getValue();
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
            contexts.put(e.getKey(), result); 
        }
        this.annotations = annotations;
    }

    /**
     * Retrieve context words for the hits.
     * 
     * @param hits hits to find contexts for
     * @param annotations the field and annotations to use for the context
     * @param contextSize how large the contexts need to be
     */
    public Contexts(Results<Hit> hits, List<Annotation> annotations, ContextSize contextSize) {
        hits.size(); // make sure all hits have been read

        List<AnnotationForwardIndex> fis = new ArrayList<>();
        for (Annotation annotation: annotations) {
            fis.add(hits.index().annotationForwardIndex(annotation));
        }

        // Get the context
        // Group hits per document
        List<Hit> hitsInSameDoc = new ArrayList<>();
        int currentDoc = -1;
        contexts = new IdentityHashMap<>(hits.size());
        for (Hit hit: hits) {
            if (hit.doc() != currentDoc) {
                if (currentDoc >= 0) {
                    try {
                        hits.threadPauser().waitIfPaused();
                    } catch (InterruptedException e) {
                        // Thread was interrupted. Just go ahead with the hits we did
                        // get, so at least we can return with valid context.
                        Thread.currentThread().interrupt();
                    }

                    // Find context for the hits in the current document
                    int[][] docContextArray = getContextWordsSingleDocument(hitsInSameDoc, contextSize, fis);

                    // Copy the contexts from the temporary Hits object to this one
                    int i = 0;
                    for (Hit hitInDoc: hitsInSameDoc) {
                        contexts.put(hitInDoc, docContextArray[i]);
                        i++;
                    }

                    // Reset hits list for next doc
                    hitsInSameDoc.clear();
                }
                currentDoc = hit.doc(); // start a new document
            }
            hitsInSameDoc.add(hit);
        }
        if (!hitsInSameDoc.isEmpty()) {
            // Find context for the hits in the current document
            int[][] docContextArray = getContextWordsSingleDocument(hitsInSameDoc, contextSize, fis);

            // Copy the contexts from the temporary Hits object to this one
            int i = 0;
            for (Hit hitInDoc: hitsInSameDoc) {
                contexts.put(hitInDoc, docContextArray[i]);
                i++;
            }
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
    public int[] get(Hit hit) {
        return contexts.get(hit);
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
        return contexts.values().iterator();
    }

}

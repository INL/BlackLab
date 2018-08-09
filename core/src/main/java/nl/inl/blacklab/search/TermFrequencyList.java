package nl.inl.blacklab.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.collections.api.map.primitive.MutableIntIntMap;
import org.eclipse.collections.api.tuple.primitive.IntIntPair;
import org.eclipse.collections.impl.factory.primitive.IntIntMaps;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.Contexts;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.util.StringUtil;

/**
 * A collection of tokens and their (absolute) frequencies.
 *
 * This class calculates the total frequency of the entries added, but you can
 * also set the total frequency explicitly (after all entries have been added)
 * if you want to calculate relative frequencies based on a different total.
 */
public class TermFrequencyList implements Iterable<TermFrequency> {
    
    /**
     * Count occurrences of context words around hit.
     * 
     * @param hits hits to get collocations for 
     * @param annotation annotation to use for the collocations, or null if default
     * @param ctx query execution context, containing the sensitivity settings
     * @param sort whether or not to sort the list by descending frequency
     *
     * @return the frequency of each occurring token
     */
    public synchronized static TermFrequencyList collocations(Hits hits, Annotation annotation, QueryExecutionContext ctx, boolean sort) {
        BlackLabIndex index = hits.index();
        if (annotation == null)
            annotation = index.mainAnnotatedField().annotations().main();
        
        // TODO: use sensitivity settings
//        if (ctx == null)
//            ctx = searcher.defaultExecutionContext(settings().concordanceField());
//        ctx = ctx.withAnnotation(annotation);
        
        Contexts contexts = new Contexts(hits, Arrays.asList(annotation));
        MutableIntIntMap coll = IntIntMaps.mutable.empty();
        for (int j = 0; j < hits.size(); j++) {
            int[] context = contexts.getContext(j);

            // Count words
            int contextHitStart = context[Contexts.CONTEXTS_HIT_START_INDEX];
            int contextRightStart = context[Contexts.CONTEXTS_RIGHT_START_INDEX];
            int contextLength = context[Contexts.CONTEXTS_LENGTH_INDEX];
            int indexInContent = Contexts.CONTEXTS_NUMBER_OF_BOOKKEEPING_INTS;
            for (int i = 0; i < contextLength; i++, indexInContent++) {
                if (i >= contextHitStart && i < contextRightStart)
                    continue; // don't count words in hit itself, just around [option..?]
                int w = context[indexInContent];
                int n;
                if (!coll.contains(w))
                    n = 1;
                else
                    n = coll.get(w) + 1;
                coll.put(w, n);
            }
        }

        // Get the actual words from the sort positions
        MatchSensitivity sensitivity = index.defaultMatchSensitivity();
        Terms terms = index.forwardIndex(contexts.getContextAnnotations().get(0)).terms();
        Map<String, Integer> wordFreq = new HashMap<>();
        for (IntIntPair e : coll.keyValuesView()) {
            int key = e.getOne();
            int value = e.getTwo();
            String word = terms.get(key);
            if (!sensitivity.isDiacriticsSensitive()) {
                word = StringUtil.stripAccents(word);
            }
            if (!sensitivity.isCaseSensitive()) {
                word = word.toLowerCase();
            }
            // Note that multiple ids may map to the same word (because of sensitivity settings)
            // Here, those groups are merged.
            Integer n = wordFreq.get(word);
            if (n == null) {
                n = 0;
            }
            n += value;
            wordFreq.put(word, n);
        }

        // Transfer from map to list
        return new TermFrequencyList(wordFreq, sort);
    }

    List<TermFrequency> list;

    long totalFrequency = 0;

    public TermFrequencyList(Map<String, Integer> wordFreq, boolean sort) {
        list = new ArrayList<>(wordFreq.size());
        for (Map.Entry<String, Integer> e : wordFreq.entrySet()) {
            list.add(new TermFrequency(e.getKey(), e.getValue()));
        }
        if (sort)
            Collections.sort(list);
    }

    TermFrequencyList(List<TermFrequency> list) {
        this.list = list;
        totalFrequency = 0;
        for (TermFrequency fr: list) {
            totalFrequency += fr.frequency;
        }
    }

    public int size() {
        return list.size();
    }

    @Override
    public Iterator<TermFrequency> iterator() {
        return list.iterator();
    }

    public TermFrequency get(int index) {
        return list.get(index);
    }

    /**
     * Get the frequency of a specific token
     *
     * @param token the token to get the frequency for
     * @return the frequency
     */
    public long getFrequency(String token) {
        // TODO: maybe speed this up by keeping a map of tokens and frequencies?
        for (TermFrequency tf : list) {
            if (tf.term.equals(token))
                return tf.frequency;
        }
        return 0;
    }

    public long getTotalFrequency() {
        return totalFrequency;
    }

    public TermFrequencyList subList(int fromIndex, int toIndex) {
        return new TermFrequencyList(list.subList(fromIndex, toIndex));
    }

}
